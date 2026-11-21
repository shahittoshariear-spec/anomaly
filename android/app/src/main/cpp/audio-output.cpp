// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#include "audio-output.h"

#include "circular-buf.hpp"

#include <chiaki/log.h>
#include <chiaki/thread.h>

#include <oboe/Oboe.h>

#include <atomic>

#define BUFFER_CHUNK_SIZE 1024
#define BUFFER_CHUNKS_COUNT 32

using AudioBuffer = CircularBuffer<BUFFER_CHUNKS_COUNT, BUFFER_CHUNK_SIZE>;

class AudioOutput;

class AudioOutputCallback: public oboe::AudioStreamCallback
{
private:
	AudioOutput *audio_output;

public:
	AudioOutputCallback(AudioOutput *audio_output) : audio_output(audio_output) {}
	oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override;
	void onErrorBeforeClose(oboe::AudioStream *stream, oboe::Result error) override;
	void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;
};

struct AudioOutput
{
	ChiakiLog *log;
	oboe::ManagedStream stream;
	AudioOutputCallback stream_callback;
	AudioBuffer buf;

	uint32_t channels = 2;
	uint32_t sample_rate = 48000;
	std::atomic<bool> reopen_requested{false};

	AudioOutput() : stream_callback(this) {}
};

static oboe::Result open_stream(AudioOutput *ao, oboe::PerformanceMode performance_mode, oboe::SharingMode sharing_mode)
{
	oboe::AudioStreamBuilder builder;
	builder.setPerformanceMode(performance_mode)
		->setSharingMode(sharing_mode)
		->setFormat(oboe::AudioFormat::I16)
		->setChannelCount(ao->channels)
		->setSampleRate(ao->sample_rate)
		->setUsage(oboe::Usage::Media)
		->setContentType(oboe::ContentType::Movie)
		->setCallback(&ao->stream_callback);
	return builder.openManagedStream(ao->stream);
}

static void start_output(AudioOutput *ao, uint32_t channels, uint32_t rate)
{
	ao->channels = channels;
	ao->sample_rate = rate;

	// Bluetooth A2DP (and some USB/HDMI routes) reject an exclusive low-latency
	// stream. Try the fast path first, then step down until one opens and runs.
	const struct
	{
		oboe::PerformanceMode performance;
		oboe::SharingMode sharing;
		const char *name;
	} configs[] = {
		{ oboe::PerformanceMode::LowLatency, oboe::SharingMode::Exclusive, "exclusive low-latency" },
		{ oboe::PerformanceMode::LowLatency, oboe::SharingMode::Shared, "shared low-latency" },
		{ oboe::PerformanceMode::None, oboe::SharingMode::Shared, "default" },
	};

	for(const auto &config : configs)
	{
		ao->stream = nullptr;
		oboe::Result result = open_stream(ao, config.performance, config.sharing);
		if(result != oboe::Result::OK)
		{
			CHIAKI_LOGI(ao->log, "Audio Output could not open %s path: %s",
					config.name, oboe::convertToText(result));
			continue;
		}

		if(!ao->stream)
			continue;

		result = ao->stream->start();
		if(result == oboe::Result::OK)
		{
			CHIAKI_LOGI(ao->log, "Audio Output started Oboe stream (%s, %u Hz, %u ch)",
					config.name, rate, channels);
			return;
		}

		CHIAKI_LOGI(ao->log, "Audio Output could not start %s path: %s",
				config.name, oboe::convertToText(result));
	}

	CHIAKI_LOGE(ao->log, "Audio Output failed to open any Oboe stream");
}

extern "C" void *android_chiaki_audio_output_new(ChiakiLog *log)
{
	auto r = new AudioOutput();
	r->log = log;
	return r;
}

extern "C" void android_chiaki_audio_output_free(void *audio_output)
{
	if(!audio_output)
		return;
	auto ao = reinterpret_cast<AudioOutput *>(audio_output);
	ao->stream = nullptr;
	delete ao;
}

extern "C" void android_chiaki_audio_output_settings(uint32_t channels, uint32_t rate, void *audio_output)
{
	auto ao = reinterpret_cast<AudioOutput *>(audio_output);
	start_output(ao, channels, rate);
}

extern "C" void android_chiaki_audio_output_frame(int16_t *buf, size_t samples_count, void *audio_output)
{
	auto ao = reinterpret_cast<AudioOutput *>(audio_output);

	// Device changes are flagged by the Oboe error callback. Reopening from here,
	// on the session's audio thread, avoids destroying the stream from inside its
	// own callback.
	if(ao->reopen_requested.exchange(false))
		start_output(ao, ao->channels, ao->sample_rate);

	size_t buf_size = samples_count * sizeof(int16_t);
	size_t pushed = ao->buf.Push(reinterpret_cast<uint8_t *>(buf), buf_size);
	if(pushed < buf_size)
		CHIAKI_LOGW(ao->log, "Audio Output Buffer Overflow!");
}

oboe::DataCallbackResult AudioOutputCallback::onAudioReady(oboe::AudioStream *stream, void *audio_data, int32_t num_frames)
{
	if(stream->getFormat() != oboe::AudioFormat::I16)
	{
		CHIAKI_LOGE(audio_output->log, "Oboe stream has invalid format in callback");
		return oboe::DataCallbackResult::Stop;
	}

	int32_t bytes_per_frame = stream->getBytesPerFrame();
	size_t buf_size_requested = static_cast<size_t>(bytes_per_frame * num_frames);
	auto buf = reinterpret_cast<uint8_t *>(audio_data);

	size_t buf_size_delivered = audio_output->buf.Pop(buf, buf_size_requested);

	if(buf_size_delivered < buf_size_requested)
	{
		CHIAKI_LOGV(audio_output->log, "Audio Output Buffer Underflow!");
		memset(buf + buf_size_delivered, 0, buf_size_requested - buf_size_delivered);
	}

	return oboe::DataCallbackResult::Continue;
}

void AudioOutputCallback::onErrorBeforeClose(oboe::AudioStream *stream, oboe::Result error)
{
	CHIAKI_LOGE(audio_output->log, "Oboe reported error before close: %s", oboe::convertToText(error));
}

void AudioOutputCallback::onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error)
{
	CHIAKI_LOGE(audio_output->log, "Oboe reported error after close: %s", oboe::convertToText(error));

	if(error == oboe::Result::ErrorDisconnected)
	{
		// Headphones or a Bluetooth device came or went. Ask the audio thread to
		// reopen on the new route; do not touch the stream from this callback.
		CHIAKI_LOGI(audio_output->log, "Audio route changed, reopening output");
		audio_output->reopen_requested.store(true);
	}
}
