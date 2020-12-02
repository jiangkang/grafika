package com.jiangkang

import android.content.Context
import android.media.*
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES20
import android.util.Log
import android.view.Surface

object MediaCodecUtils {

    const val TAG = "MediaCodecUtils"

    @JvmStatic
    fun createMediaCodec(context: Context) {
        val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
        val format = MediaFormat.createVideoFormat(mimeType, 600, 600)
        format.apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 6000000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 40)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }
        val codec = MediaCodec.createEncoderByType(mimeType)
        val surface: Surface
        codec.apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = createInputSurface()
        }.start()
        var eglDisplay = EGL14.EGL_NO_DISPLAY
        var eglConfig: EGLConfig
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val eglConfigAttribList = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
        )
        val numEglConfigs = IntArray(1)
        val eglConfigs = arrayOfNulls<EGLConfig>(1)
        if (!EGL14.eglChooseConfig(eglDisplay, eglConfigAttribList, 0,
                        eglConfigs, 0, eglConfigs.size, numEglConfigs, 0)) {
            return
        }
        if (numEglConfigs[0] <= 0) {
            return
        }
        eglConfig = eglConfigs[0]!!
        val eglContextAttribList = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        )
        val eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig,
                EGL14.EGL_NO_CONTEXT, eglContextAttribList, 0)
        val eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, eglConfigAttribList, 0)

        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        repeat(100) {

            generateFrame(it)
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, computePresentationTimeNsec(it))
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }
        val newFormat: MediaFormat = codec.outputFormat
        val muxer = MediaMuxer(context.filesDir.absolutePath + "/media.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer.apply {
            addTrack(newFormat)
            start()
        }

    }

    @JvmStatic
    fun printCodecInfo() {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        codecList.codecInfos.forEach { codecInfo ->
            Log.d(TAG, "${codecInfo.name},${if (codecInfo.isEncoder) "encoder" else "decoder"},${codecInfo.supportedTypes.joinToString()}")
        }
    }


    private fun generateFrame(frameIndex: Int) {
        var frameIndex = frameIndex
        val BOX_SIZE = 80
        frameIndex %= 240
        val xpos: Int
        val ypos: Int
        val absIndex = Math.abs(frameIndex - 120)
        xpos = absIndex * 600 / 120
        ypos = absIndex * 600 / 120
        val lumaf = absIndex / 120.0f
        GLES20.glClearColor(lumaf, lumaf, lumaf, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(BOX_SIZE / 2, ypos, BOX_SIZE, BOX_SIZE)
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glScissor(xpos, BOX_SIZE / 2, BOX_SIZE, BOX_SIZE)
        GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    private fun computePresentationTimeNsec(frameIndex: Int): Long {
        val ONE_BILLION: Long = 1000000000
        return frameIndex * ONE_BILLION / 40
    }


}