#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler2; // Lightmap
uniform vec4 ColorModulator;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord1;
in vec4 normal;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    
    if (color.a < 0.1) {
        discard;
    }
    
    vec4 lightColor = texture(Sampler2, texCoord1);
    fragColor = color * lightColor;
}
