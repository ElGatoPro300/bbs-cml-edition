#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 FormColorGrade;

in vec2 texCoord0;

out vec4 fragColor;

float bbsHue2Rgb(float p, float q, float t)
{
    if (t < 0.0) t += 1.0;
    if (t > 1.0) t -= 1.0;
    if (t < 1.0 / 6.0) return p + (q - p) * 6.0 * t;
    if (t < 1.0 / 2.0) return q;
    if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
    return p;
}

vec3 bbsRgb2Hsl(vec3 c)
{
    float maxVal = max(c.r, max(c.g, c.b));
    float minVal = min(c.r, min(c.g, c.b));
    float h = 0.0;
    float s = 0.0;
    float l = (maxVal + minVal) * 0.5;

    if (maxVal != minVal)
    {
        float d = maxVal - minVal;
        s = l > 0.5 ? d / (2.0 - maxVal - minVal) : d / (maxVal + minVal);

        if (maxVal == c.r)
        {
            h = (c.g - c.b) / d + (c.g < c.b ? 6.0 : 0.0);
        }
        else if (maxVal == c.g)
        {
            h = (c.b - c.r) / d + 2.0;
        }
        else
        {
            h = (c.r - c.g) / d + 4.0;
        }

        h /= 6.0;
    }

    return vec3(h, s, l);
}

vec3 bbsHsl2Rgb(vec3 hsl)
{
    float h = hsl.x;
    float s = hsl.y;
    float l = hsl.z;

    if (s == 0.0)
    {
        return vec3(l);
    }

    float q = l < 0.5 ? l * (1.0 + s) : l + s - l * s;
    float p = 2.0 * l - q;

    return vec3(
        bbsHue2Rgb(p, q, h + 1.0 / 3.0),
        bbsHue2Rgb(p, q, h),
        bbsHue2Rgb(p, q, h - 1.0 / 3.0)
    );
}

vec3 bbsApplyColorGrade(vec3 rgb)
{
    if (abs(FormColorGrade.x) < 0.001 && abs(FormColorGrade.y) < 0.001 && abs(FormColorGrade.z) < 0.001 && abs(FormColorGrade.w) < 0.001)
    {
        return rgb;
    }

    vec3 outRgb = rgb;

    if (abs(FormColorGrade.x) >= 0.001)
    {
        outRgb += FormColorGrade.x;
    }

    if (abs(FormColorGrade.y) >= 0.001)
    {
        outRgb = vec3(0.5) + (1.0 + FormColorGrade.y) * (outRgb - vec3(0.5));
    }

    if (abs(FormColorGrade.w) >= 0.001)
    {
        vec3 hsl = bbsRgb2Hsl(clamp(outRgb, 0.0, 1.0));
        hsl.y = clamp(hsl.y * (1.0 + FormColorGrade.w), 0.0, 1.0);
        outRgb = bbsHsl2Rgb(hsl);
    }

    if (abs(FormColorGrade.z) > 0.01)
    {
        vec3 hsl = bbsRgb2Hsl(clamp(outRgb, 0.0, 1.0));
        hsl.x = fract(hsl.x + FormColorGrade.z / 360.0);
        if (hsl.x < 0.0)
        {
            hsl.x += 1.0;
        }
        outRgb = bbsHsl2Rgb(hsl);
    }

    return clamp(outRgb, 0.0, 1.0);
}

void main()
{
    vec4 color = texture(Sampler0, texCoord0);

    if (color.a == 0.0)
    {
        discard;
    }

    vec3 graded = bbsApplyColorGrade(color.rgb);

    fragColor = vec4(graded * ColorModulator.rgb, color.a * ColorModulator.a);
}
