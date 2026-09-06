package mchorse.bbs_mod.utils.iris;

import net.irisshaders.iris.vertices.ImmediateState;

import java.util.function.Supplier;

public final class IrisCustomPass
{
    public static <T> T run(Supplier<T> draw)
    {
        boolean level = ImmediateState.isRenderingLevel;
        boolean extended = ImmediateState.renderWithExtendedVertexFormat;

        try
        {
            ImmediateState.isRenderingLevel = false;
            ImmediateState.renderWithExtendedVertexFormat = false;

            return draw.get();
        }
        finally
        {
            ImmediateState.isRenderingLevel = level;
            ImmediateState.renderWithExtendedVertexFormat = extended;
        }
    }
}
