#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;
uniform float WaveAmplitude;
uniform float WaveFrequency;
uniform float WaveSpeed;
uniform float Time;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec2 texCoord1; // Lightmap (normalized)
out vec4 normal;

// Hash functions for noise
float hash12(vec2 p) {
    vec3 p3  = fract(vec3(p.xyx) * .1031);
    p3 += dot(p3, p3.yzx + 19.19);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(.1031, .1030, .0973));
    p3 += dot(p3, p3.yzx+19.19);
    return fract((p3.xx+p3.yz)*p3.zy);
}

float getWaveHeight(vec2 pos, float time) {
    // 1. Procedural rain ripples (adapted from ripples.glsl)
    float rippleHeight = 0.0;
    float cell_density = 1.0 * WaveFrequency; // Use frequency as density
    vec2 uv = pos * cell_density;
    vec2 p0 = floor(uv);
    
    // Check neighbors for ripples
    for (int j = -1; j <= 1; ++j) {
        for (int i = -1; i <= 1; ++i) {
            vec2 pi = p0 + vec2(i, j);
            vec2 hsh = pi;
            vec2 p = pi + hash22(hsh);
            
            // Random start time for each cell
            float t = fract(0.5 * time + hash12(hsh));
            vec2 v = p - uv;
            
            // Distance from ripple center
            float d = length(v) - (2.0 * t);
            
            // Ring wave function
            float h = 1e-2;
            float d1 = d - h;
            float d2 = d + h;
            float p1 = sin(20.0 * d1) * smoothstep(-0.6, -0.3, d1) * smoothstep(0., -0.3, d1);
            float p2 = sin(20.0 * d2) * smoothstep(-0.6, -0.3, d2) * smoothstep(0., -0.3, d2);
            
            rippleHeight += (p2 - p1) * (1.0 - t);
        }
    }
    
    // 2. Large sine waves for ocean feel
    float oceanHeight = sin((pos.x + pos.y) * 0.5 + time * 2.0) * 0.2;
    oceanHeight += sin((pos.x - pos.y) * 0.3 + time * 1.5) * 0.2;
    
    return (rippleHeight * 0.5 + oceanHeight) * WaveAmplitude;
}

void main() {
    vec3 pos = Position;
    
    // Calculate height at current position
    float h = getWaveHeight(pos.xz, Time * WaveSpeed);
    pos.y += h;
    
    // Approximate normal
    float delta = 0.1;
    float hx = getWaveHeight(pos.xz + vec2(delta, 0.0), Time * WaveSpeed);
    float hz = getWaveHeight(pos.xz + vec2(0.0, delta), Time * WaveSpeed);
    
    vec3 newNormal = normalize(vec3(h - hx, delta, h - hz));
    normal = vec4(newNormal, 0.0);

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = length((ModelViewMat * vec4(pos, 1.0)).xyz);
    vertexColor = Color;
    texCoord0 = UV0;
    texCoord1 = vec2(UV2) / 256.0; 
}
