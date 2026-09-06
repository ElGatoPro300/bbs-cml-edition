# ModelForm

`ModelForm` is the most complex form type: animated rigs, poses, actions, and shape keys.

* **Class**: `mchorse.bbs_mod.forms.forms.ModelForm`

## Properties

* `model`: String ID of the model
* `pose`: Base `Pose` transformation (bones)
* `poseOverlay`: Secondary `Pose` for animations on top of the base pose
* `actions`: `ActionsConfig` mapping abstract actions (e.g. `"running"`) to specific animations
* `shapeKeys`: `ShapeKeys` for OBJ morph targets

## Renderer pipeline

**Renderer**: `ModelFormRenderer`

Typical path:

`render3D()` → `ensureAnimator()` → `animator.applyActions()` → `model.applyPose()` → `renderModel()`

* **IAnimator**: `Animator` (standard) or `ProceduralAnimator` (code-driven)
* **MatrixCache**: Caches bone matrices for attaching BodyParts or Items

## Agent notes

* Attachment / item / body-part bugs often involve matrix cache timing vs pose application.
* Prefer existing animator paths before inventing a parallel pose stack.
* See also: `docs/architecture/rendering-iris.md`, `docs/architecture/forms.md`
