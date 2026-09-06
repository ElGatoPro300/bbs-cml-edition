# Form system

Forms dictate the visual appearance of actors and objects.

* **Base class**: `mchorse.bbs_mod.forms.forms.Form`
* **Format support**: Handles `.bbs.json` (custom), `.obj`, and `.bobj` (binary object), among other loaders depending on form type.
* **BodyPart system**: `BodyPart.java` attaches child forms to parent bones.
  * *Extending*: Add `Value` fields to `BodyPart` to store new attachment properties.
* **Standard forms**: Model, Billboard, Label, Extruded, Block, Item, Mob, Particle.

## Typical pieces of a form

1. Data class extending `Form` with `Value` properties
2. Client `FormRenderer`
3. Editor panel (`UIFormPanel`) when the form is user-editable
4. Registration on common + client + editor (see how-to)

## Related deep-dives

* ModelForm (rigs / animator): `docs/architecture/model-form.md`
* Particle form: `docs/architecture/particle-form.md`
* How to add a form: `docs/architecture/how-to-form.md`
* Rendering constraints: `docs/architecture/rendering-iris.md`
