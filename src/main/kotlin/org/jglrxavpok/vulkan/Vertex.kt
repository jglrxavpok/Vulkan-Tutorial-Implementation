package org.jglrxavpok.vulkan

import org.joml.Vector2fc
import org.joml.Vector3fc
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import java.nio.ByteBuffer

data class Vertex(val pos: Vector2fc, val color: Vector3fc) {


    companion object {

        const val SizeOfVertex = (3+2) * 4 // component count * sizeof(float32)

        fun createBindingDescriptions(): VkVertexInputBindingDescription.Buffer {
            val bindingDescription = VkVertexInputBindingDescription.calloc(1)
            bindingDescription.binding(0)
            bindingDescription.stride(SizeOfVertex)
            bindingDescription.inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

            return bindingDescription
        }

        fun createAttributeDescriptions(): VkVertexInputAttributeDescription.Buffer {
            val attributeDescriptions = VkVertexInputAttributeDescription.calloc(2)

            attributeDescriptions[0]
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32_SFLOAT)
                    .offset(0)

            attributeDescriptions[1]
                    .binding(0)
                    .location(1)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(2 * 4)

            return attributeDescriptions
        }

        fun fillVertexData(vertices: Array<Vertex>, vertexData: ByteBuffer) {
            val data = vertexData.asFloatBuffer()
            for(vertex in vertices) {
                vertex.pos.get(data)
                data.position(data.position()+2)
                vertex.color.get(data)
                data.position(data.position()+3)
            }
        }
    }
}