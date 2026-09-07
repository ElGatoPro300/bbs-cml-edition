package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.iris.ShaderCurves.ShaderVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Event allowing addons to register custom shader variables, curves,
 * and uniforms for Iris shaders.
 */
public class RegisterShaderCurvesEvent
{
    private static final List<Consumer<Map<String, ShaderVariable>>> customVariableListeners = new ArrayList<>();
    private static final List<String> customUniformNames = new ArrayList<>();

    public void registerVariable(ShaderVariable variable)
    {
        if (variable != null)
        {
            ShaderCurves.variableMap.put(variable.name, variable);
        }
    }

    public void registerVariable(String name, String defaultValue, boolean isInteger)
    {
        if (name != null)
        {
            ShaderCurves.variableMap.put(name, new ShaderVariable(name, defaultValue, isInteger));
        }
    }

    public void registerCustomUniform(String uniformName)
    {
        if (uniformName != null)
        {
            customUniformNames.add(uniformName);
        }
    }

    public void registerListener(Consumer<Map<String, ShaderVariable>> listener)
    {
        if (listener != null)
        {
            customVariableListeners.add(listener);
        }
    }

    public static List<String> getCustomUniformNames()
    {
        return customUniformNames;
    }

    public static void populateVariables(Map<String, ShaderVariable> variableMap)
    {
        for (Consumer<Map<String, ShaderVariable>> listener : customVariableListeners)
        {
            try
            {
                listener.accept(variableMap);
            }
            catch (Throwable ignored)
            {}
        }
    }
}
