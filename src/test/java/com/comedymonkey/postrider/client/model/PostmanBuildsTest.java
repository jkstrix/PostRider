package com.comedymonkey.postrider.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.postman.Roster;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;

class PostmanBuildsTest {
	private static Set<String> names(ModelPart part) {
		Set<String> names = new HashSet<>();
		collect(part, names);
		return names;
	}

	private static void collect(ModelPart part, Set<String> names) {
		for (String child : new String[] {"root", "body", "head", "hat", "harmonica", "right_arm", "left_arm", "hammer", "potion", "canteen",
			"pocket_watch", "letter", "striker", "flint", "satchel", "satchel_flat", "satchel_full", "right_leg", "left_leg", "hair_back", "hair_bun",
			"hair_braid", "hair_pigtails"})
			if (part.hasChild(child)) {
				names.add(child);
				collect(part.getChild(child), names);
			}
	}

	@Test
	void bothBuildsHaveTheSamePartsSoEveryAnimationWorksOnBoth() {
		Set<String> regular = names(PostmanModel.createBodyLayer().bakeRoot());
		Set<String> slim = names(PostmanModel.createSlimBodyLayer().bakeRoot());
		assertEquals(regular, slim);
		assertTrue(regular.containsAll(PostmanModel.HAIR_PARTS));
	}

	@Test
	void everyRidersHairHasAPart() {
		for (Roster.Rider rider : Roster.ALL)
			if (rider.hair().part() != null)
				assertTrue(PostmanModel.HAIR_PARTS.contains(rider.hair().part()), rider.id());
		for (Roster.Hair hair : Roster.Hair.values())
			if (hair != Roster.Hair.SHORT)
				assertTrue(PostmanModel.HAIR_PARTS.contains(hair.part()), hair.name());
	}

	@Test
	void theSlimBuildHasANarrowerHeadAndArms() {
		ModelPart regular = PostmanModel.createBodyLayer().bakeRoot().getChild("root").getChild("body");
		ModelPart slim = PostmanModel.createSlimBodyLayer().bakeRoot().getChild("root").getChild("body");
		assertEquals(8, width(regular.getChild("head")), 1e-6);
		assertEquals(7, width(slim.getChild("head")), 1e-6);
		for (String arm : new String[] {"right_arm", "left_arm"}) {
			assertEquals(4, width(regular.getChild(arm)), 1e-6, arm);
			assertEquals(3, width(slim.getChild(arm)), 1e-6, arm);
		}
	}

	/** How wide a part's own box is (its first cube). */
	private static float width(ModelPart part) {
		float[] extent = {Float.MAX_VALUE, -Float.MAX_VALUE};
		part.visit(new PoseStack(), (pose, path, index, cube) -> {
			if (path.isEmpty() && index == 0) {
				extent[0] = cube.minX;
				extent[1] = cube.maxX;
			}
		});
		return extent[1] - extent[0];
	}
}
