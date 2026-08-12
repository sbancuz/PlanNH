#version 120

uniform sampler2D uTexture;
uniform vec2 uStep;
uniform float uOffsets[25];
uniform float uWeights[25];

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec4 color = vec4(0.0);
    float total = 0.0;
    for (int i = 0; i < 25; i++) {
        color += texture2D(uTexture, uv + uStep * uOffsets[i]) * uWeights[i];
        total += uWeights[i];
    }
    gl_FragColor = color / total;
}
