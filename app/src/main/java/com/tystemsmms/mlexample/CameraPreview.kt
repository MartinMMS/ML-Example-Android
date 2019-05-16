/*
 * Copyright (c) 2019 T-Systems Multimedia Solutions GmbH
 * Riesaer Str. 5, D-01129 Dresden, Germany
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Autor: mtd
 * Datum: 08.05.2019
 */
package com.tystemsmms.mlexample

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.view.Surface
import android.view.TextureView
import timber.log.Timber
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import android.view.WindowManager
import java.util.*
import kotlin.math.max

class CameraPreview constructor(val context: Context) {

    private var currentCameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSize: Size? = null

    val availableCameraIds: List<String>
        get() {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            return manager.cameraIdList.asList()
        }

    var selectedCameraId: String? = null
        set(value) {
            field = value
            startCaptureSession()
        }

    var textureView: TextureView? = null
        set(value) {
            field?.surfaceTextureListener = null
            field = value
            field?.surfaceTextureListener = surfaceTextureListener
        }

    var surfaceTexture: SurfaceTexture? = null

    private var surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            setTransformation()
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            stopCaptureSession()
            return false
        }

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture?, width: Int, height: Int) {
            if (texture != null) {
                surfaceTexture = texture
                startCaptureSession()
            } else {
                stopCaptureSession()
            }
        }
    }

    //transforms the camera preview to fit center crop into the TextureView
    private fun setTransformation() {

        /* TODO: transform camera preview
        if (textureView != null && previewSize != null) {
            val view = textureView!!
            val camera = previewSize!!

            val viewCenterX = view.width.toFloat()/2
            val viewCenterY = view.height.toFloat()/2

            val matrix = Matrix()

            // TextureView automatically does a image rotation if the camera sensor orientation was not 0
            // to bring the picture upright in default device orientation.
            // After that, TextureView does a stretching scaling of x and y with no respect to aspect ratio.
            // We want to honor the aspect ratio, so we first need to undo the stretch-scaling.
            var reScaleX = camera.width.toFloat() / view.width
            var reScaleY = camera.height.toFloat() / view.height
            matrix.setScale(reScaleX, reScaleY, viewCenterX, viewCenterY)

            //second we rotate the image if the device is +/-90 degree rotated to its default orientation
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val rotation = windowManager.defaultDisplay.rotation
            val rotateDegrees = when (rotation) {
                Surface.ROTATION_90 -> -90
                Surface.ROTATION_270 -> 90
                else -> 0
            }.toFloat()
            matrix.postRotate(rotateDegrees, viewCenterX, viewCenterY)

            //third we do a non stretching scaling
            var scaleX = 0f
            var scaleY = 0f
            if (rotateDegrees == 0f) {
                //image was not rotated so take imageSizeX/imageSizeY as they are
                scaleX = view.width.toFloat() / camera.width
                scaleY = view.height.toFloat() / camera.height
            } else {
                //image was rotated so swap imageSizeX/imageSizeY
                scaleX = view.width.toFloat() / camera.height
                scaleY = view.height.toFloat() / camera.width
            }
            val scale : Float = max(scaleX, scaleY) // Scale center-crop. You may change this line to 'min' to scale center-inside.
            matrix.postScale(scale, scale, viewCenterX, viewCenterY)

            view.setTransform(matrix) //apply the transformation to the contents of TextureView
        }
        */
    }

    @Throws(SecurityException::class)
    private fun startCaptureSession() {
        try {
            if (currentCameraDevice?.id != selectedCameraId) {
                stopCaptureSession()
            }

            if (surfaceTexture != null && selectedCameraId != null) {
                val texture = surfaceTexture
                val cameraId = selectedCameraId!!
                Timber.v("capture session gets created")
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        val characteristics = manager.getCameraCharacteristics(camera.id)

                        // use the largest available image size for this camera
                        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        val largest = map.getOutputSizes(SurfaceTexture::class.java).maxWith(CompareSizesByArea())
                        if (largest == null) {
                            Timber.e(Exception("Found no camera size which fits on the display."))
                            return
                        }

                        //If the camera outputs its picture rotated then swap imageSizeX<->imageSizeY of the usedPreviewSize accordingly.
                        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                        val usedPreviewSize = when {
                            //TODO: take sensor orientation into account
                            //(sensorOrientation == 90 || sensorOrientation == 270) -> Size(largest.height, largest.width)
                            else -> largest
                        }

                        texture!!.setDefaultBufferSize(usedPreviewSize.width, usedPreviewSize.height)
                        val surface = Surface(texture)
                        camera.createCaptureSession(
                            listOf(surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    val captureRequestBuilder =
                                        camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                    captureRequestBuilder.addTarget(surface)
                                    captureRequestBuilder.set(
                                        CaptureRequest.CONTROL_MODE,
                                        CameraMetadata.CONTROL_MODE_AUTO
                                    )
                                    session.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                                    captureSession = session
                                    currentCameraDevice = camera
                                    previewSize = usedPreviewSize
                                    setTransformation()
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    Timber.e("configuring capture session failed ")
                                    stopCaptureSession()
                                }
                            },
                            null
                        )
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        stopCaptureSession()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        stopCaptureSession()
                    }
                }, null)
            }
        } catch (e: Exception) {
            Timber.e(e, "opening capture session failed");
        }
    }

    private fun stopCaptureSession() {
        captureSession?.close()
        captureSession = null
        currentCameraDevice?.close()
        currentCameraDevice = null
        previewSize = null
    }

    fun isCaptureSessionActive(): Boolean {
        return (captureSession != null)
    }

    fun findBackCameraId() : String? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (cameraId in availableCameraIds) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                continue
            }
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
            return cameraId
        }
        return null
    }

    /** Compares two `Size`s based on their areas.  */
    private class CompareSizesByArea : Comparator<Size> {
        override fun compare(s1: Size, s2: Size): Int {
            return (s1.width.toLong() * s1.height - s2.width.toLong() * s2.height).toInt()
        }
    }

}