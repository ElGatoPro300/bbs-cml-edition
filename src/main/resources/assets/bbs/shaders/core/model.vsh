#version 330

#moj_import <bbs:model_effects.glsl>

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;


out float vertexDistance;
out vec4 vertexColor;
out vec4 rawVertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec4 normal;
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

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv)
{
    return texture(lightMap, clamp(vec2(uv) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

void main()
{
    /* Vanilla 1.21.1 mobs: VertexConsumer bakes camera-relative world into Position
     * (Y-up, no view rotation); ModelViewMat is only the view rotation at draw time.
     * Terrain uses the same space via Position + ChunkOffset. FogMat holds that
     * camera-relative model transform; ModelViewMat stays view × FogMat for clip. */
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexDistance = fog_distance((FogMat * vec4(Position, 1.0)).xyz, FogShape);
    vec3 n = NormalMat * Normal;
    float nLen2 = dot(n, n);
    vec3 fixNormal = nLen2 > 1.0e-8 ? n * inversesqrt(nLen2) : vec3(0.0, 0.0, 1.0);
    rawVertexColor = Color;
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, fixNormal, Color);
    /* Filtered sample (not texelFetch): continuous lightmap UVs keep float lighting
     * intermediates (brightness 0–1 and fixed levels 0–15 without truncate). */
    lightMapColor = minecraft_sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
    normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);
    formRootPos = (FormRootInverse * vec4(Position, 1.0)).xyz;
}
