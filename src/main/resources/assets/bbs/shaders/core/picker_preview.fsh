#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform int Target;
uniform vec4 BoneHighlight;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

/*
 * Gizmo handle ids 1-16 (see Gizmo.java) are reserved before any bone/form pick id starts
 * (StencilMap.setup() starts allocating bone ids at Gizmo.STENCIL_HANDLE_MAX + 1 = 17), so this
 * must branch on the exact handle id rather than a single ">= 7" cutoff, otherwise every
 * plane/scale/rotate/trackball handle added after the original 7 solo ids falls through to the
 * generic BoneHighlight color instead of its own axis tint.
 */
vec4 gizmoPreviewColor(int index)
{
    /* X-axis family: solo translate/scale/rotate bar + Combined-mode scale/rotate handles. */
    if (index == 1 || index == 8 || index == 11)
    {
        return vec4(1.0, 0.35, 0.35, 0.75);
    }

    /* Y-axis family. */
    if (index == 2 || index == 9 || index == 12)
    {
        return vec4(0.40, 1.0, 0.45, 0.75);
    }

    /* Z-axis family. */
    if (index == 3 || index == 10 || index == 13)
    {
        return vec4(0.35, 0.62, 1.0, 0.75);
    }

    /* XZ plane (red + blue blend). */
    if (index == 4)
    {
        return vec4(1.0, 0.35, 1.0, 0.75);
    }

    /* XY plane (red + green blend). */
    if (index == 5)
    {
        return vec4(1.0, 0.95, 0.25, 0.75);
    }

    /* ZY plane (blue + green blend). */
    if (index == 6)
    {
        return vec4(0.30, 0.90, 0.95, 0.75);
    }

    /* Screen-move/uniform-scale cube, trackball sphere and screen handle. */
    if (index == 7 || index == 14 || index == 15)
    {
        return vec4(1.0, 0.85, 0.25, 0.75);
    }

    /* View/arcball ring. */
    if (index == 16)
    {
        return vec4(1.0, 1.0, 1.0, 0.75);
    }

    if (index >= 17)
    {
        return BoneHighlight;
    }

    return vec4(1.0, 1.0, 1.0, 0.5);
}

int decodePickId(vec4 color)
{
    return int(color.r * 255.0) | (int(color.g * 255.0) << 8) | (int(color.b * 255.0) << 16);
}

void main()
{
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;

    if (color.a < 1.0)
    {
        discard;
    }

    int totalIndex = decodePickId(color);

    if (totalIndex == Target)
    {
        color = gizmoPreviewColor(Target);
    }
    else
    {
        discard;
    }

    fragColor = color * ColorModulator;
}
