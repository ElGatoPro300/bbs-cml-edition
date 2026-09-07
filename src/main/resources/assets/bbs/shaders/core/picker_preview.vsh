#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV1;

out vec2 texCoord0;
flat out int Target;
flat out vec4 BoneHighlight;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    Target = (UV1.x & 0xffff) | ((UV1.y & 0xff) << 16);
    BoneHighlight = Color;
}
