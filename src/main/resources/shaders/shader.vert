#version 450
#extension GL_ARB_separate_shader_objects : enable

out gl_PerVertex {
    vec4 gl_Position;
};

layout(location = 0) in vec2 vert_position;
layout(location = 1) in vec3 vert_color;

layout(location = 0) out vec3 fragColor;

void main() {
    gl_Position = vec4(vert_position, 0.0, 1.0);
    fragColor = vert_color;
}