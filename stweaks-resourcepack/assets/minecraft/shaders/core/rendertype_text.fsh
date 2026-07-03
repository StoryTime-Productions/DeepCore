#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }

    fragColor = apply_fog(
        color,
        sphericalVertexDistance,
        cylindricalVertexDistance,
        FogEnvironmentalStart,
        FogEnvironmentalEnd,
        FogRenderDistanceStart,
        FogRenderDistanceEnd,
        FogColor
    );

    // Sidebar score remover condition.
    if (
        ScreenSize.x - gl_FragCoord.x < 69.0
        && fragColor.a == 1.0
        && fragColor.r == 0.988235294
        && fragColor.g == 0.329411765
        && fragColor.b == 0.329411765
    ) {
        discard;
    }
}
