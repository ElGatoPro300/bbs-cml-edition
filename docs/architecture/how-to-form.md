# How to create a new Form (with UI)

1. **Data class**: `public class MyForm extends Form`. Define properties as `Value` fields.
2. **Renderer**: `public class MyFormRenderer extends FormRenderer<MyForm>`. Implement `render()`.
3. **UI panel**: `public class UIMyFormPanel extends UIFormPanel<MyForm>`. Add fields via `this.add()`.
4. **Registration**:
   * **Common**: `FormArchitect.register("my_form", MyForm.class);`
   * **Client**: `FormUtilsClient.register(MyForm.class, MyFormRenderer::new);`
   * **Editor**: `UIFormEditor.register(MyForm.class, UIMyFormPanel::new);`

## Checklist

* Read wiki + `docs/architecture/forms.md` before inventing a parallel pattern
* Localization for panel labels: `docs/architecture/ui-localization.md`
* Renderer Iris/shadow rules: `docs/architecture/rendering-iris.md`
* Values/serialization: `docs/architecture/values-and-registration.md`

## Related

* ModelForm specifics: `docs/architecture/model-form.md`
* Addons: `docs/ADDONS.md`
