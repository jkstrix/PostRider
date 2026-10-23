package com.comedymonkey.postrider.content.postman.route;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;

/** Made-up ground for planner tests: solid below y 64, open above, with whatever's been put on it; unknown far out. */
final class GridTerrain implements Terrain {
	static final int GROUND = 64;
	private final Map<Long, Kind> blocks = new HashMap<>();
	private final java.util.Set<Long> ice = new java.util.HashSet<>();
	private final int reach;

	GridTerrain() {
		this(400);
	}

	/** Blocks further than {@code reach} from the middle count as not loaded. */
	GridTerrain(int reach) {
		this.reach = reach;
	}

	GridTerrain fill(int x1, int y1, int z1, int x2, int y2, int z2, Kind kind) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
					blocks.put(BlockPos.asLong(x, y, z), kind);
		return this;
	}

	/** The columns between these corners stand on ice. */
	GridTerrain ice(int x0, int z0, int x1, int z1) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
				ice.add(BlockPos.asLong(x, 0, z));
		return this;
	}

	@Override
	public boolean icy(int x, int y, int z) {
		return ice.contains(BlockPos.asLong(x, 0, z));
	}

	@Override
	public Kind kind(int x, int y, int z) {
		if (Math.abs(x) > reach || Math.abs(z) > reach)
			return Kind.UNKNOWN;
		Kind kind = blocks.get(BlockPos.asLong(x, y, z));
		if (kind != null)
			return kind;
		return y < GROUND ? Kind.SOLID : Kind.OPEN;
	}

	@Override
	public boolean covered(int x, int y, int z) {
		for (int above = y; above < 200; above++)
			if (!kind(x, above, z).isClear())
				return true;
		return false;
	}
}
