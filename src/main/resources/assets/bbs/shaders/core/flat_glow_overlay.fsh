#version 150

uniform sampler2D Sampler0;
uniform float GlowIntensity;
uniform float GlowSize;
uniform float GlowSpread;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

float bbsSampleA(vec2 uv)
{
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0)
    {
        return 0.0;
    }

    return texture(Sampler0, uv).a;
}

/* Fixed-iteration dilate — no dynamic breaks (some drivers discard the whole program). */
float bbsDilate(vec2 uv, float radiusPx)
{
    float center = bbsSampleA(uv);
    float radius = abs(radiusPx);

    if (radius < 0.02)
    {
        return center;
    }

    vec2 texel = 1.0 / max(vec2(textureSize(Sampler0, 0)), vec2(1.0));
    float best = center;

    for (int ring = 1; ring <= 8; ring++)
    {
        float t = float(ring) / 8.0;
        float r = radius * t;

        for (int i = 0; i < 16; i++)
        {
            float ang = float(i) * 0.39269908169;
            vec2 dir = vec2(cos(ang), sin(ang));

            best = max(best, bbsSampleA(uv + dir * texel * r));
        }
    }

    return best;
}

void main()
{
    float src = bbsSampleA(texCoord0);
    float sizeM = GlowSize;
    float spreadM = clamp(GlowSpread, 0.0, 1.0);
    float intensity = max(GlowIntensity, 0.0);

    float radiusPx = max(sizeM, 0.0) * 12.0;
    float coverage = bbsDilate(texCoord0, radiusPx);

    /* Outer Glow fringe (outside silhouette). */
    float choke = mix(0.35, 0.88, spreadM);
    float outer = max(0.0, coverage - src * choke);
    /* Wider softEdge = cleaner, less muddy rim. */
    float softEdge = mix(1.45, 0.28, spreadM);
    float outerMask = smoothstep(0.0, softEdge, outer);

    /* Always keep on-sprite emission so Intensity alone still glows. */
    float coreMask = src * mix(0.5, 0.85, spreadM);
    float glowMask = max(coreMask, outerMask);

    float strength = intensity / (1.0 + intensity * 0.1);
    float alpha = glowMask * strength * max(vertexColor.a, 0.2);

    if (alpha < 0.002)
    {
        discard;
    }

    /* Keep tint clean — avoid over-boost that washes dirty under HDR. */
    vec3 rgb = vertexColor.rgb * mix(1.0, 1.15, clamp(strength * 0.35, 0.0, 1.0));

    fragColor = vec4(rgb, clamp(alpha, 0.0, 1.0));
}
