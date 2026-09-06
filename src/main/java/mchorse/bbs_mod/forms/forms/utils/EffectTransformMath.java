package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.utils.MathUtils;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Builds inverse transform matrices and evaluates soft paint masks (box / circle / triangle).
 */
public class EffectTransformMath
{
    public static final float EPSILON = 0.001F;
    public static final float BILLBOARD_MASK_HALF = 0.5F;
    /** Centered mask half for item forms in form-root space. */
    public static final float ITEM_MASK_HALF_BASE = 0.5F;
    /**
     * Item display geometry sits above the mask origin; subtract this from scale Y so UI 0
     * matches the calibrated neutral mask (previously required ~-0.2 in the editor).
     */
    public static final float ITEM_MASK_SCALE_Y_BIAS = -0.2F;
    /** Half-height at scale 1.0; full vertical span is 2x this value (feet to head for humanoids). */
    public static final float MODEL_MASK_HALF_BASE = 1F;
    public static final float MODEL_MASK_Y_BIAS = 1F;
    /**
     * Legacy fixed half for structures (~2 blocks). Prefer
     * {@link #resolveStructureMaskHalfExtents(EffectTransform, Vector3f, float, float, float)}.
     */
    public static final float STRUCTURE_MASK_HALF_BASE = 2F;
    /** Circle/ellipsoid must grow by √3 so scale 1 covers AABB corners. */
    public static final float STRUCTURE_CIRCLE_COVER = 1.7320508F;
    /** Triangle prism enlarge so scale 1 covers the structure AABB corners. */
    public static final float STRUCTURE_TRIANGLE_COVER = 3F;

    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Vector3f LOCAL = new Vector3f();

    private EffectTransformMath()
    {}

    public static boolean isTransformActive(EffectTransform transform)
    {
        return transform != null && transform.isActive();
    }

    public static void resolveModelMaskHalfExtents(EffectTransform transform, Vector3f dest)
    {
        resolveMaskHalfExtents(transform, dest, MODEL_MASK_HALF_BASE, MODEL_MASK_Y_BIAS);
    }

    public static void resolveBillboardMaskHalfExtents(EffectTransform transform, Vector3f dest)
    {
        resolveBillboardMaskHalfExtents(transform, dest, BILLBOARD_MASK_HALF, BILLBOARD_MASK_HALF);
    }

    /**
     * Billboard / flat-quad masks sized to the actual half extents of the drawn quad
     * (aspect-scaled billboards are often narrower or shorter than the unit 0.5 box).
     */
    public static void resolveBillboardMaskHalfExtents(EffectTransform transform, Vector3f dest, float quadHalfX, float quadHalfY)
    {
        float baseX = Math.max(Math.abs(quadHalfX), EPSILON);
        float baseY = Math.max(Math.abs(quadHalfY), EPSILON);

        if (transform == null)
        {
            dest.set(baseX, baseY, BILLBOARD_MASK_HALF);

            return;
        }

        float scaleX = transform.scaleX == 0F ? 0.001F : transform.scaleX;
        float scaleY = transform.scaleY == 0F ? 0.001F : transform.scaleY;
        float scaleZ = transform.scaleZ == 0F ? 0.001F : transform.scaleZ;

        dest.set(baseX * scaleX, baseY * scaleY, BILLBOARD_MASK_HALF * scaleZ);
    }

    public static void resolveBlockMaskHalfExtents(EffectTransform transform, Vector3f dest)
    {
        resolveMaskHalfExtents(transform, dest, 0.5F, 1F);
    }

    /**
     * Block-form spatial masks sized to the rendered block AABB (signs, chests, …).
     * Unlike {@link #resolveStructureMaskHalfExtents}, dimensions are not clamped to a
     * minimum of one block — thin standing signs keep narrow X/Z. {@code size*} are full
     * spans in block-local space (Y from the block bottom upward). Extra Y half-extent
     * covers soft-mask falloff so scale 1 still reaches the top of tall entity models.
     */
    public static void resolveBlockVisualMaskHalfExtents(EffectTransform transform, Vector3f dest, float sizeX, float sizeY, float sizeZ)
    {
        float baseX = Math.max(sizeX, EPSILON) * 0.5F;
        float baseY = Math.max(sizeY, EPSILON) * 0.5F;
        float baseZ = Math.max(sizeZ, EPSILON) * 0.5F;
        float scaleX = 1F;
        float scaleY = 1F;
        float scaleZ = 1F;
        float cover = 1F;

        if (transform != null)
        {
            scaleX = transform.scaleX == 0F ? 0.001F : transform.scaleX;
            scaleY = transform.scaleY == 0F ? 0.001F : transform.scaleY;
            scaleZ = transform.scaleZ == 0F ? 0.001F : transform.scaleZ;
            cover = structureShapeCover(transform.shape);
        }

        dest.set(
            baseX * scaleX * cover,
            baseY * scaleY * cover,
            baseZ * scaleZ * cover
        );

        /* Bottom-anchored soft falloff — scale with each axis so 1 → 0.99 shrinks proportionally. */
        float falloff = resolveMaskFalloff(transform, dest);

        dest.x += falloff * Math.max(Math.abs(scaleX), EPSILON);
        dest.y += falloff * Math.max(Math.abs(scaleY), EPSILON);
        dest.z += falloff * Math.max(Math.abs(scaleZ), EPSILON);
    }

    public static void resolveStructureMaskHalfExtents(EffectTransform transform, Vector3f dest)
    {
        resolveStructureMaskHalfExtents(transform, dest, STRUCTURE_MASK_HALF_BASE * 2F, STRUCTURE_MASK_HALF_BASE * 2F, STRUCTURE_MASK_HALF_BASE * 2F);
    }

    /**
     * Structure paint/color masks: UI scale 1 covers the full structure AABB for box,
     * circle, and triangle; scale 0 covers nothing. Negative scale shrinks through zero
     * and clears the mask (same as model/billboard/label) — do not abs the half extents
     * or negative scale mirrors back to a visible positive mask.
     * {@code size*} are block counts.
     */
    public static void resolveStructureMaskHalfExtents(EffectTransform transform, Vector3f dest, float sizeX, float sizeY, float sizeZ)
    {
        float baseX = Math.max(sizeX, 1F) * 0.5F;
        float baseY = Math.max(sizeY, 1F) * 0.5F;
        float baseZ = Math.max(sizeZ, 1F) * 0.5F;
        float scaleX = 1F;
        float scaleY = 1F;
        float scaleZ = 1F;
        float cover = 1F;

        if (transform != null)
        {
            scaleX = transform.scaleX == 0F ? 0.001F : transform.scaleX;
            scaleY = transform.scaleY == 0F ? 0.001F : transform.scaleY;
            scaleZ = transform.scaleZ == 0F ? 0.001F : transform.scaleZ;
            cover = structureShapeCover(transform.shape);
        }

        dest.set(
            baseX * scaleX * cover,
            baseY * scaleY * cover,
            baseZ * scaleZ * cover
        );
    }

    private static float structureShapeCover(PaintMaskShape shape)
    {
        if (shape == PaintMaskShape.CIRCLE)
        {
            return STRUCTURE_CIRCLE_COVER;
        }

        if (shape == PaintMaskShape.TRIANGLE)
        {
            return STRUCTURE_TRIANGLE_COVER;
        }

        return 1F;
    }

    public static void resolveItemMaskHalfExtents(EffectTransform transform, Vector3f dest)
    {
        if (transform == null)
        {
            dest.set(ITEM_MASK_HALF_BASE, ITEM_MASK_HALF_BASE * resolveItemScaleY(1F), ITEM_MASK_HALF_BASE);

            return;
        }

        float scaleX = transform.scaleX == 0F ? 0.001F : transform.scaleX;
        float scaleY = resolveItemScaleY(transform.scaleY);
        float scaleZ = transform.scaleZ == 0F ? 0.001F : transform.scaleZ;

        dest.set(ITEM_MASK_HALF_BASE * scaleX, ITEM_MASK_HALF_BASE * scaleY, ITEM_MASK_HALF_BASE * scaleZ);
    }

    private static float resolveItemScaleY(float scaleY)
    {
        if (Math.abs(scaleY) < EPSILON)
        {
            scaleY = 0F;
        }

        return scaleY + ITEM_MASK_SCALE_Y_BIAS;
    }

    public static void resolveMaskHalfExtents(EffectTransform transform, Vector3f dest, float baseHalf, float yBias)
    {
        if (transform == null)
        {
            dest.set(baseHalf, baseHalf * yBias, baseHalf);

            return;
        }

        float scaleX = transform.scaleX == 0F ? 0.001F : transform.scaleX;
        float scaleY = transform.scaleY == 0F ? 0.001F : transform.scaleY;
        float scaleZ = transform.scaleZ == 0F ? 0.001F : transform.scaleZ;

        dest.set(baseHalf * scaleX, baseHalf * yBias * scaleY, baseHalf * scaleZ);
    }

    /**
     * Inverse of translate + pivot rotation. Scale is applied via mask half-extents.
     * Order matches limb transforms: T(offset) · T(pivot) · R · T(-pivot).
     */
    public static void buildInverseMatrix(EffectTransform transform, Matrix4f dest)
    {
        if (transform == null)
        {
            dest.identity();

            return;
        }

        MATRIX.identity()
            .translate(transform.offsetX, transform.offsetY, transform.offsetZ)
            .translate(transform.pivotX, transform.pivotY, transform.pivotZ)
            .rotateXYZ(MathUtils.toRad(transform.rotateX), MathUtils.toRad(transform.rotateY), MathUtils.toRad(transform.rotateZ))
            .translate(-transform.pivotX, -transform.pivotY, -transform.pivotZ);

        dest.set(MATRIX);
        dest.invert();
    }

    /**
     * Soft unit-box mask in effect-local space. Full strength inside the oriented box,
     * smooth falloff near edges.
     */
    public static float mask3DModel(float x, float y, float z, EffectTransform transform, Vector3f halfExtents)
    {
        return mask3DModel(x, y, z, transform, halfExtents, true);
    }

    /**
     * Soft mask in effect-local space. When {@code bottomAnchoredY} is true the volume spans
     * y in [0, 2*halfY] so scale Y maps to model height from the feet upward.
     */
    public static float mask3DModel(float x, float y, float z, EffectTransform transform, Vector3f halfExtents, boolean bottomAnchoredY)
    {
        if (!isTransformActive(transform))
        {
            return 1F;
        }

        return evaluateSoftMask(x, y, z, transform, halfExtents, bottomAnchoredY);
    }

    /**
     * Billboard paint mask. When the transform is inactive the full quad is painted
     * (same as {@link #mask3DModel}); once move/scale/rotate is edited the soft volume applies.
     */
    public static float maskBillboard(float x, float y, float z, EffectTransform transform)
    {
        return maskBillboard(x, y, z, transform, BILLBOARD_MASK_HALF, BILLBOARD_MASK_HALF);
    }

    /**
     * Billboard mask sized to the drawn quad's half extents (see
     * {@link #resolveBillboardMaskHalfExtents(EffectTransform, Vector3f, float, float)}).
     */
    public static float maskBillboard(float x, float y, float z, EffectTransform transform, float quadHalfX, float quadHalfY)
    {
        if (!isTransformActive(transform))
        {
            return 1F;
        }

        Vector3f half = new Vector3f();

        resolveBillboardMaskHalfExtents(transform, half, quadHalfX, quadHalfY);

        return evaluateSoftMask(x, y, z, transform, half, false);
    }

    /**
     * Billboard mask using precomputed half extents (avoids per-vertex allocation).
     */
    public static float maskBillboard(float x, float y, float z, EffectTransform transform, Vector3f halfExtents)
    {
        if (!isTransformActive(transform))
        {
            return 1F;
        }

        return evaluateSoftMask(x, y, z, transform, halfExtents, false);
    }

    /**
     * Soft rim thickness in form/local units, derived from unscaled mask half extents
     * so transform scale keeps a stable gradient and per-axis scale does not bleed.
     */
    public static float resolveMaskFalloff(EffectTransform transform, Vector3f scaledHalfExtents)
    {
        if (scaledHalfExtents == null)
        {
            return EPSILON;
        }

        float scaleX = 1F;
        float scaleY = 1F;
        float scaleZ = 1F;

        if (transform != null)
        {
            scaleX = transform.scaleX == 0F ? EPSILON : Math.abs(transform.scaleX);
            scaleY = transform.scaleY == 0F ? EPSILON : Math.abs(transform.scaleY);
            scaleZ = transform.scaleZ == 0F ? EPSILON : Math.abs(transform.scaleZ);
        }

        float baseX = Math.abs(scaledHalfExtents.x) / scaleX;
        float baseY = Math.abs(scaledHalfExtents.y) / scaleY;
        float baseZ = Math.abs(scaledHalfExtents.z) / scaleZ;
        float baseMax = Math.max(baseX, Math.max(baseY, baseZ));

        return Math.max(baseMax * 0.15F, EPSILON);
    }

    private static float evaluateSoftMask(float x, float y, float z, EffectTransform transform, Vector3f halfExtents, boolean bottomAnchoredY)
    {
        buildInverseMatrix(transform, MATRIX);
        LOCAL.set(x, y, z);
        MATRIX.transformPosition(LOCAL);

        if (bottomAnchoredY)
        {
            LOCAL.y -= halfExtents.y;
        }

        float maxHalf = Math.max(halfExtents.x, Math.max(halfExtents.y, halfExtents.z));

        /* Scale 0 → empty mask (no residual falloff speck). */
        if (maxHalf < EPSILON)
        {
            return 0F;
        }

        PaintMaskShape shape = transform == null ? PaintMaskShape.BOX : transform.shape;
        float dist;
        float falloff = resolveMaskFalloff(transform, halfExtents);

        if (shape == PaintMaskShape.CIRCLE)
        {
            float hx = Math.max(halfExtents.x, EPSILON);
            float hy = Math.max(halfExtents.y, EPSILON);
            float hz = Math.max(halfExtents.z, EPSILON);
            float qx = LOCAL.x / hx;
            float qy = LOCAL.y / hy;
            float qz = LOCAL.z / hz;
            float radius = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);

            if (radius <= 1F)
            {
                return 1F;
            }

            float localLen = (float) Math.sqrt(LOCAL.x * LOCAL.x + LOCAL.y * LOCAL.y + LOCAL.z * LOCAL.z);

            dist = (radius - 1F) * localLen / radius;
        }
        else if (shape == PaintMaskShape.TRIANGLE)
        {
            /* Front-facing triangle in XY (apex up), thickness along Z — matches chest paint. */
            float dTri = sdTriangleXY(LOCAL.x, LOCAL.y, halfExtents.x, halfExtents.y);
            float dZ = Math.abs(LOCAL.z) - halfExtents.z;
            float outsideTri = Math.max(dTri, 0F);
            float outsideZ = Math.max(dZ, 0F);

            dist = (float) Math.sqrt(outsideTri * outsideTri + outsideZ * outsideZ);
            dist += Math.min(Math.max(Math.max(dTri, dZ), 0F), 0F);
        }
        else
        {
            float edgeX = Math.abs(LOCAL.x) - halfExtents.x;
            float edgeY = Math.abs(LOCAL.y) - halfExtents.y;
            float edgeZ = Math.abs(LOCAL.z) - halfExtents.z;
            float outsideX = Math.max(edgeX, 0F);
            float outsideY = Math.max(edgeY, 0F);
            float outsideZ = Math.max(edgeZ, 0F);

            dist = (float) Math.sqrt(outsideX * outsideX + outsideY * outsideY + outsideZ * outsideZ);
            dist += Math.min(Math.max(Math.max(edgeX, Math.max(edgeY, edgeZ)), 0F), 0F);
        }

        if (dist <= 0F)
        {
            return 1F;
        }

        if (dist >= falloff)
        {
            return 0F;
        }

        return 1F - dist / falloff;
    }

    /**
     * Signed distance to an isosceles triangle in XY sized by half extents
     * (apex at +Y, base at -Y spanning ±halfX) — front-facing on the model.
     */
    private static float sdTriangleXY(float x, float y, float halfX, float halfY)
    {
        float ax = 0F;
        float ay = Math.max(halfY, EPSILON);
        float bx = -Math.max(halfX, EPSILON);
        float by = -Math.max(halfY, EPSILON);
        float cx = Math.max(halfX, EPSILON);
        float cy = -Math.max(halfY, EPSILON);

        return sdTriangle2D(x, y, ax, ay, bx, by, cx, cy);
    }

    private static float sdTriangle2D(float px, float py, float ax, float ay, float bx, float by, float cx, float cy)
    {
        float e0x = bx - ax;
        float e0y = by - ay;
        float e1x = cx - bx;
        float e1y = cy - by;
        float e2x = ax - cx;
        float e2y = ay - cy;
        float v0x = px - ax;
        float v0y = py - ay;
        float v1x = px - bx;
        float v1y = py - by;
        float v2x = px - cx;
        float v2y = py - cy;
        float d0 = distToSegmentSq(v0x, v0y, e0x, e0y);
        float d1 = distToSegmentSq(v1x, v1y, e1x, e1y);
        float d2 = distToSegmentSq(v2x, v2y, e2x, e2y);
        float minDistSq = Math.min(d0, Math.min(d1, d2));
        float s = Math.signum(e0x * e2y - e0y * e2x);
        float o0 = s * (v0x * e0y - v0y * e0x);
        float o1 = s * (v1x * e1y - v1y * e1x);
        float o2 = s * (v2x * e2y - v2y * e2x);
        float inside = Math.min(o0, Math.min(o1, o2));

        return (float) (-Math.sqrt(minDistSq) * Math.signum(inside));
    }

    private static float distToSegmentSq(float vx, float vy, float ex, float ey)
    {
        float denom = ex * ex + ey * ey;
        float t = denom <= EPSILON ? 0F : MathUtils.clamp((vx * ex + vy * ey) / denom, 0F, 1F);
        float dx = vx - ex * t;
        float dy = vy - ey * t;

        return dx * dx + dy * dy;
    }

    public static float maskBlock(float x, float y, float z, EffectTransform transform)
    {
        Vector3f half = new Vector3f();

        resolveBlockMaskHalfExtents(transform, half);

        return mask3DModel(x - 0.5F, y, z - 0.5F, transform, half);
    }
}
