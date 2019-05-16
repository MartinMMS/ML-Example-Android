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

import android.Manifest

import android.content.pm.PackageManager

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import com.tystemsmms.mlexample.tflite.Classifier
import com.tystemsmms.mlexample.tflite.Model
import kotlin.math.round

class MainActivity : AppCompatActivity() {

    private val cameraPreview: CameraPreview = CameraPreview(this)
    private val mainThread = Handler(Looper.getMainLooper())
    private var imageClassifier : Classifier? = null

    private val backgroundThread: HandlerThread = HandlerThread("CameraBackgroundThread").apply {
        start()
    }
    private val backgroundHandler: Handler = Handler(backgroundThread.looper).also { hander ->
        val runnable = object : Runnable {
            override fun run() {
                onBackgroundHandlerTick()
                hander.postDelayed(this, 1000)
            }
        }
        hander.post(runnable)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!permissionsGranted(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                200
            )
        }

        val model = Model.createFromAssets(this, "IMAGE_CLASSIFIER.lite", Model.ModelType.FLOAT, 224, 224, "IMAGE_CLASSIFIER.labels")
        imageClassifier = Classifier.create(model, Classifier.Device.NNAPI, 3)
        selectedModelTextView.text = "Model: from assets folder"

        cameraPreview.textureView = textureView
    }

    private fun permissionsGranted(vararg permissions: String): Boolean {
        var result = true
        for (permission in permissions) {
            result = result && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        return result
    }

    private fun onBackgroundHandlerTick() {
        if (cameraPreview.isCaptureSessionActive()) {
            Timber.d("taking picture now")
            imageClassifier?.let { classifier ->
                textureView.getBitmap(classifier.model.imageSizeX, classifier.model.imageSizeY)
                    ?.apply {
                        try {
                            val recognitions = classifier.recognizeImage(this)
                            if (recognitions != null) {
                                val recognitionStringBuilder = StringBuilder()
                                for (recognition in recognitions) {
                                    recognition.apply {
                                        val confidencePercent = round(confidence?.times(100) ?: 0f)
                                        if (confidencePercent >= 20) { //show only results with more than 20%
                                            recognitionStringBuilder.appendln("$title ($confidencePercent%)")
                                        }
                                    }
                                }
                                val recognitionString = recognitionStringBuilder.toString()
                                Timber.d("Recognition: $recognitionString")
                                mainThread.post {
                                    resultsTextView.text = recognitionString
                                }
                            }
                            recycle()
                        } catch (e: Exception) {
                            Timber.e(e)
                        }
                    }
                    ?: Timber.d("no image available for classification")
            }
        } else {
            Timber.d("no capture session available to take a picture")
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.v("onResume")
        cameraPreview.findBackCameraId()
            ?.let { backCameraId ->
                cameraPreview.selectedCameraId = backCameraId
            }
            ?: Timber.v(Exception("No camera on the back side of the device was found!"))

    }

    override fun onPause() {
        Timber.v("onPause")
        cameraPreview.selectedCameraId = null
        super.onPause()
    }

    override fun onDestroy() {
        backgroundThread.quit()
        super.onDestroy()
    }

}
