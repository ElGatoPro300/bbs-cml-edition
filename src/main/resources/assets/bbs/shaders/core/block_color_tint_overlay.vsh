#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 FormRootInverse;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;

out vec4 vertexColor;
out vec2 texCoord0;
out vec3 formRootPos;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    texCoord0 = UV0;
    /* Position is already stack-transformed by the block VertexConsumer; including
     * ModelViewMat here made color masks follow the camera in world renders. */
    formRootPos = (FormRootInverse * vec4(Position, 1.0)).xyz;
}
