#version 330

#moj_import <bbs:model_effects.glsl>

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV1;
in ivec2 UV2;
in vec3 Normal;


out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 formRootPos;

float fog_distance(vec3 pos, int shape)
{
    if (shape == 0)
    {
        return length(pos);
    }
    else
    {
        float distXZ = length(pos.xz);
        float distY = abs(pos.y);
        return max(distXZ, distY);
    }
}

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    /* Position is already stack-transformed; FogMat maps it to camera-relative Y-up. */
    vec3 fogPos = (FogMat * vec4(Position, 1.0)).xyz;
    sphericalVertexDistance = fog_spherical_distance(fogPos);
    cylindricalVertexDistance = fog_cylindrical_distance(fogPos);
    vertexDistance = fog_distance(fogPos, FogShape);
    vertexColor = Color;
    texCoord0 = UV0;
    formRootPos = (FormRootInverse * vec4(Position, 1.0)).xyz;
}
