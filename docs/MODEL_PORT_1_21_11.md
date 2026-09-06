# Model rendering: 1.21.11 base pass

## Submission change

ModelInstance's ordinary geometry pass now submits BufferBuilder triangles through
BillboardRenderLayers. This binds the texture view, sampler, lightmap, overlay and
the vanilla entity shader's uniform buffers. Raw VAO/glUseProgram draws and the
compatibility BufferRenderer are no longer used for that base pass.

CubicLayerRenderer inherits CubicCubeRenderer's hierarchy transforms, cube pivots,
mesh transforms, normals and shape-key evaluation. It batches each group's geometry
by material, resolves the existing material textures and bone texture overrides, and
uses vertex colors for instance/form/group tint and alpha. Invisible groups and cubes
are omitted. Bone lighting and the model's culling setting are preserved.

BOBJModelVAO.renderLayer uses the existing CPU weighted skinning and processData hook
(including the simple-player joint deformation). It reads the resulting positions and
normals directly, without transform feedback or uploading legacy VBOs during that draw.
Triangles are split into runs by dominant bone for bone tint, lighting and texture
crossfades. The existing BOBJ root rotation and armature setup remain in ModelInstance.
GLTF/FBX models converted to BOBJ use the same route.

Form-level texture crossfades use the existing two-pass path for ordinary rendering;
the new vanilla shader cannot read the old TextureBlendActive uniform. Bone-level
crossfades likewise draw their two resolved textures explicitly.

## GUI composition correction (2026-09-06)

The earlier cache-only framebuffer change was reverted by the user. Global cell
coordinates and render-pass scissors fixed scrolling, but immediate 3D previews
still drew before deferred GUI backgrounds. Batcher2D.flush is empty, so translucent
panel/selection backgrounds tinted and darkened the models at GUI composition time.

All forms reporting is3D() now render into per-cell ModelPreviewRenderer targets owned by
UIRenderingContext. Each queued image has a distinct target for the frame; targets
are reused next frame and closed when UIScreen is removed. Geometry and mouse orbit
use cell-local coordinates and a cell-sized orthographic projection. The final GUI quad uses local cell
coordinates and captures the DrawContext matrix/scissor, preserving scrolling and
clipping. Root layers place it after the preceding backgrounds. Framebuffer bindings,
viewport and preview uniforms are restored after the 3D draw.

The GUI target is also bound as a GL framebuffer for forms that still draw directly;
RenderLayer draws use the same attachments through RenderSystem output overrides.
GUI state restoration uses GlStateManager for blend, depth and culling to avoid
desynchronizing vanilla's cached GL state between consecutive previews.

The optimized menu routes all 3D previews through this same path, preserving its
fixed-angle behavior but temporarily bypassing the raw framebuffer thumbnail cache.
This may increase rendering cost for large lists; caching deferred GPU previews is
remaining optimization work. 2D forms retain their existing cache/render paths.

Validation: compileClientJava passed and runClient completed resource initialization.
Visual acceptance of this composition fix is pending: test selected and unselected
skins, mobs, blocks, structures, scroll, menu reopen, and optimized mode on/off. Existing light values were
not increased to compensate for the panel overlay.

## Limits

### Iris pipeline compatibility (2026-09-06)

With Iris 1.10.7 for 1.21.11, BufferBuilder can return IrisVertexFormats.ENTITY.
The previous vanilla-only format comparison selected the unlit GUI layout for
these buffers (billboard_0/2/6 and particle_ui in latest.log). Those layouts have
different strides and attributes. BillboardRenderLayers and ParticleRenderLayers
now recognize the extended entity layout. Pipeline builders retain vanilla layout
constants so Iris can extend them dynamically, including after toggling shaders.

Custom pipelines also need Iris program mappings. IrisFormPipelines copies the
vanilla entity/particle main and shadow mappings, preserving contextual selection
for hands and block entities. Unlit POSITION_TEXTURE_COLOR draws explicitly map
to TEXTURED_COLOR and SHADOW_TEX_COLOR. Iris 1.10.7 exposes copyPipeline but not a
public shadow assignment method, so IrisPipelinesAccessor invokes assignToShadow.
These signatures and vertex formats were checked against the local remapped jar.
The trail base pass now uses BillboardRenderLayers with its actual texture rather
than the BufferRenderer shim's GUI pipeline.

The first runtime test with Complementary exposed an additional PBR wrapper crash:
Iris now calls AbstractTexture.getGlTexture rather than the legacy getGlId method.
IrisTextureWrapper now resolves non-owning GPU texture/view adapters for its selected
animation frame and processed normal/specular map, or returns its fallback texture.

Validation: runClient compiled these changes. The initial PBR crash was reproduced
and corrected; visual verification with ComplementaryReimagined_r5.8.1_IRLights remains pending.
Check enabling/disabling the pack in the same session, model visibility, shadows,
billboards/extruded forms, particles and trails. Legacy paint/glow overlay passes
are not covered by the base trail migration.

This is a base rendering migration, not a completed migration of every model effect.
Picking/stencil and paint/color-grade overlay passes retain their legacy paths. Custom
per-fragment color grading, spatial masks, glow, PBR and Iris pack-specific behavior
are not certified by this change. Special effect redraws need further work.

CPU deformation and per-frame vertex upload prioritize restoring correct geometry.
Large meshes and many animated actors need performance measurement before considering
the port complete. Legacy VAO allocation still exists for the unmigrated passes.

## Validation

Final compileClientJava passed, and runClient initialized the latest build. The user's
screenshot shows player/alex geometry in the world and textured skin thumbnails.
This confirms base visibility, but does not establish animation or import correctness.
Next, animate arms/legs and check
the outer skin layer. Check a cubic model with shape keys, a model with multiple
materials, bone visibility/tint, texture crossfades and a skinned BOBJ/GLTF model.
Verify terrain occlusion and mirrored/nonuniform transforms separately from effects.
