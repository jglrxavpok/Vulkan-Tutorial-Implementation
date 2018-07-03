package org.jglrxavpok.vulkan

import org.joml.Matrix4f
import java.nio.ByteBuffer

data class UniformBufferObject(val model: Matrix4f = Matrix4f(), val view: Matrix4f = Matrix4f(), val proj: Matrix4f = Matrix4f()) {
    fun putIn(uboData: ByteBuffer) {
        val fb = uboData.asFloatBuffer()
        model.get(fb)
        fb.position(fb.position()+16)
        view.get(fb)
        fb.position(fb.position()+16)
        proj.get(fb)
        fb.position(fb.position()+16)
    }

    companion object {
        const val SizeOfUniformBufferObject = (4*4) * 3 * 4
    }
}