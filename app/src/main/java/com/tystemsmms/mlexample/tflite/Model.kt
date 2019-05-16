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
package com.tystemsmms.mlexample.tflite

import android.content.Context
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.ArrayList

class Model private constructor(val modelByteBuffer: ByteBuffer, val type: ModelType, val imageSizeX: Int, val imageSizeY: Int, val labels: List<String>){

    /** The modelType type used for classification.  */
    enum class ModelType {
        FLOAT,
        QUANTIZED
    }

    companion object {

        fun createFromAssets(context: Context, modelAssetName: String, modelType: ModelType, imageSizeX: Int, imageSizeY: Int, labelsAssetName: String): Model {
            val byteBuffer = loadModelFromAsset(context, modelAssetName)
            val labels = loadLabelsFromAsset(context, labelsAssetName)
            return Model(byteBuffer, modelType, imageSizeX, imageSizeY, labels)
        }

        fun createFromFiles(modelFile: File, modelType: ModelType, imageSizeX: Int, imageSizeY: Int, labelsFile: File): Model {
            val byteBuffer = loadModelFromFile(modelFile)
            val labels = loadLabelsFromFile(labelsFile)
            return Model(byteBuffer, modelType, imageSizeX, imageSizeY, labels)
        }

        private fun loadModelFromFile(modelFile: File): ByteBuffer {
            val inputStream = FileInputStream(modelFile)
            val fileChannel = inputStream.channel
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
        }

        private fun loadModelFromAsset(context: Context, modelAssetName: String): ByteBuffer {
            val fileDescriptor = context.assets.openFd(modelAssetName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }

        private fun loadLabelsFromAsset(context: Context, labelsAssetName: String): List<String> {
            val assetFileInputStream = context.assets.open(labelsAssetName)
            return loadLabelsFromInputStream(assetFileInputStream)
        }

        private fun loadLabelsFromFile(labelFile: File): List<String> {
            val inputStream = FileInputStream(labelFile)
            return loadLabelsFromInputStream(inputStream)
        }

        private fun loadLabelsFromInputStream(labelsInputStream: InputStream): List<String> {
            val labels = ArrayList<String>()
            BufferedReader(InputStreamReader(labelsInputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    labels.add(line)
                    line = reader.readLine()
                }
            }
            return labels
        }

    }

}