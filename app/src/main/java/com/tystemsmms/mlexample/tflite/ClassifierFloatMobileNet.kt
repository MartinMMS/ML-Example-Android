/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.tystemsmms.mlexample.tflite

import java.io.IOException

/** This TensorFlowLite classifier works with the float MobileNet modelType.  */
class ClassifierFloatMobileNet
/**
 * Initializes a `ClassifierFloatMobileNet`.
 *
 * @param activity
 */
@Throws(IOException::class)
constructor(model: Model, device: Classifier.Device, numThreads: Int) : Classifier(model, device, numThreads) {

    /**
     * An array to hold inference results, to be feed into Tensorflow Lite as outputs. This isn't part
     * of the super class, because we need a primitive array here.
     */
    private var labelProbArray: Array<FloatArray>? = null

    override// Float.SIZE / Byte.SIZE;
    val numBytesPerChannel: Int
        get() = 4

    init {
        labelProbArray = Array(1) { FloatArray(numLabels) }
    }

    override fun addPixelValue(pixelValue: Int) {
        imgData!!.putFloat((pixelValue shr 16 and 0xFF) / 255f)
        imgData!!.putFloat((pixelValue shr 8 and 0xFF) / 255f)
        imgData!!.putFloat((pixelValue and 0xFF) / 255f)
    }

    override fun getProbability(labelIndex: Int): Float {
        return labelProbArray!![0][labelIndex]
    }

    override fun setProbability(labelIndex: Int, value: Number) {
        labelProbArray!![0][labelIndex] = value.toFloat()
    }

    override fun getNormalizedProbability(labelIndex: Int): Float {
        return labelProbArray!![0][labelIndex]
    }

    override fun runInference() {
        tflite!!.run(imgData!!, labelProbArray!!)
    }
}
