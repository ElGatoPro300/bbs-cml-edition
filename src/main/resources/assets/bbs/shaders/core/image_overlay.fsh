#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform int BlendMode;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main()
{
    vec4 tex = texture(Sampler0, texCoord0);
    vec4 tint = vertexColor * ColorModulator;
    float alpha = tex.a * tint.a;

    if (alpha <= 0.0)
    {
        discard;
    }

    vec3 baseRgb = tex.rgb * tint.rgb;

    if (BlendMode == 0)
    {
        /* 0: Normal — standard un-premultiplied color for standard alpha blending */
        fragColor = vec4(baseRgb, alpha);
    }
    else if (BlendMode == 1)
    {
        /* 1: Multiply — lerp between 1.0 (no change) and baseRgb (multiply) */
        fragColor = vec4(mix(vec3(1.0), baseRgb, alpha), alpha);
    }
    else if (BlendMode == 7 || BlendMode == 8)
    {
        /* 7: Overlay (2*src*dst via DST_COLOR+SRC_COLOR) & 8: Color Dodge (src*src + dst):
         * baseRgb * sqrt(alpha) ensures smooth alpha fading with quadratic/bilinear blend factors */
        fragColor = vec4(baseRgb * sqrt(alpha), alpha);
    }
    else
    {
        /* 2: Screen, 3: Add, 4: Saturation, 5: Incrustation, 6: Exclusion:
         * Premultiplying base RGB by alpha ensures both vertex/opacity alpha and
         * base PNG texture transparency smoothly blend with the background */
        fragColor = vec4(baseRgb * alpha, alpha);
    }
}
