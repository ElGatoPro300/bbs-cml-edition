# StructureForm: missing static blocks

The base renderer split the structure into two submission paths. Static blocks such
as logs and stone used a raw VAO and the legacy BBS model shader. Biome-tinted blocks
(including leaves), animated textures and translucent blocks used RenderLayer buffers.
This explains why the tree could show leaves while its trunk was absent.

The normal world and UI base draws now submit static blocks through the existing
renderLayerGroup method. Minecraft selects the moving-block/entity block layer and
binds its atlas, lightmap and pipeline uniforms. Block models retain their UVs,
per-vertex colors, light and ambient occlusion. Neighbor culling still uses the virtual
structure view, whose missing positions return air rather than surrounding world blocks.

StructureData caches a static-block group when loading NBT and clears it on reload.
It excludes animated, biome-tinted and translucent blocks, which retain their existing
passes. Block entity rendering also remains separate. Base drawing is no longer
conditional on a non-null legacy VAO; legacy VAOs remain for effects and pixel picking.

Validation: compileClientJava passed. Visual acceptance is pending: reload the tree
at full opacity without effects and check the trunk and leaves from several angles,
then check stone, glass, animated blocks and the UI preview. Large structures now
rebuild their static block vertices per draw, so performance needs checking as well.
This fix does not certify legacy shader effects, pixel picking, or the soft-opacity
depth-stamp path under Iris. No particle renderer was changed.
