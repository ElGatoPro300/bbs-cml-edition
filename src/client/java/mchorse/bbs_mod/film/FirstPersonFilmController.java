package mchorse.bbs_mod.film;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

public class FirstPersonFilmController extends WorldFilmController
{
    public FirstPersonFilmController(Film film)
    {
        super(film);
    }

    @Override
    protected void renderEntity(WorldRenderContext context, Replay replay, IEntity entity, int index)
    {
        if (replay.fp.get())
        {
            return;
        }

        super.renderEntity(context, replay, entity, index);
    }
}