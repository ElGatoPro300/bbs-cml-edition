#version 330

uniform sampler2D Sampler0;

uniform mat4 PaintEffectInverse;
uniform float PaintEffectActive;
uniform vec3 PaintMaskHalf;
uniform float PaintMaskBottomAnchored;
uniform float PaintMaskShape;
uniform vec4 GlowOverlayColor;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 formRootPos;

out vec4 fragColor;

float bbsSdTriangle2D(vec2 p, vec2 a, vec2 b, vec2 c)
{
    vec2 e0 = b - a;
    vec2 e1 = c - b;
    vec2 e2 = a - c;
    vec2 v0 = p - a;
    vec2 v1 = p - b;
    vec2 v2 = p - c;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / max(dot(e0, e0), 0.0001), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / max(dot(e1, e1), 0.0001), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / max(dot(e2, e2), 0.0001), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
                     vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
                     vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));

    return -sqrt(max(d.x, 0.0)) * sign(d.y);
}

float bbsPaintEffectMask(vec3 rootPos, mat4 effectInverse, float activeFlag, vec3 halfExtents, float bottomAnchored, float shape)
{
    if (activeFlag < 0.5)
    {
        return 1.0;
    }

    vec3 local = (effectInverse * vec4(rootPos, 1.0)).xyz;

    if (bottomAnchored > 0.5)
    {
        local.y -= halfExtents.y;
    }

    float dist;
    float maxHalf = max(halfExtents.x, max(halfExtents.y, halfExtents.z));

    /* Scale 0 → empty mask. */
    if (maxHalf < 0.001)
    {
        return 0.0;
    }

    if (shape > 1.5)
    {
        /* Front-facing triangle in XY (apex up), thickness along Z. */
        vec2 halfXY = max(halfExtents.xy, vec2(0.001));
        vec2 a = vec2(0.0, halfXY.y);
        vec2 b = vec2(-halfXY.x, -halfXY.y);
        vec2 c = vec2(halfXY.x, -halfXY.y);
        float dTri = bbsSdTriangle2D(local.xy, a, b, c);
        float dZ = abs(local.z) - halfExtents.z;

        dist = length(max(vec2(dTri, dZ), 0.0)) + min(max(dTri, dZ), 0.0);
    }
    else if (shape > 0.5)
    {
        vec3 safeHalf = max(halfExtents, vec3(0.001));
        float radius = length(local / safeHalf);

        dist = (radius - 1.0) * maxHalf;
    }
    else
    {
        vec3 d = abs(local) - halfExtents;

        dist = length(max(d, 0.0)) + min(max(max(d.x, d.y), d.z), 0.0);
    }

    float falloff = max(maxHalf * 0.15, 0.001);

    return 1.0 - smoothstep(0.0, falloff, dist);
}

void main()
{
    vec4 tex = texture(Sampler0, texCoord0);
    float mask = bbsPaintEffectMask(formRootPos, PaintEffectInverse, PaintEffectActive, PaintMaskHalf, PaintMaskBottomAnchored, PaintMaskShape);
    float alpha = tex.a * vertexColor.a * mask;

    if (alpha < 0.01)
    {
        discard;
    }

    vec3 color = vertexColor.rgb;
    float glowStrength = GlowOverlayColor.a;

    if (glowStrength > 0.001)
    {
        if (glowStrength >= 1.0)
        {
            color += GlowOverlayColor.rgb * glowStrength;
        }
        else
        {
            vec3 emissive = color + GlowOverlayColor.rgb;

            color = mix(color, emissive, glowStrength);
        }
    }

    fragColor = vec4(color, alpha);
}
