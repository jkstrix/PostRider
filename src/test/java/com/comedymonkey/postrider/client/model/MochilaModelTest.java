package com.comedymonkey.postrider.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.testutil.ModResourceFiles;

import net.minecraft.client.model.animal.equine.HorseModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

class MochilaModelTest {
	@Test
	void sitsOnTheVanillaHorseBody() {
		ModelPart mochila = MochilaModel.createBodyLayer().bakeRoot().getChild("mochila");
		ModelPart body = LayerDefinition.create(HorseModel.createBodyMesh(CubeDeformation.NONE), 64, 64).bakeRoot().getChild("body");
		assertEquals(body.x, mochila.x);
		assertEquals(body.y, mochila.y);
		assertEquals(body.z, mochila.z);
	}

	@Test
	void followsTheBodyWhenItMoves() {
		ModelPart body = LayerDefinition.create(HorseModel.createBodyMesh(CubeDeformation.NONE), 64, 64).bakeRoot().getChild("body");
		body.xRot = 0.6F;
		body.y = 8.0F;
		MochilaModel model = new MochilaModel(MochilaModel.createBodyLayer().bakeRoot());
		model.followBody(body);
		assertEquals(0.6F, model.part().xRot);
		assertEquals(8.0F, model.part().y);
	}

	@Test
	void hasItsTextures() {
		assertNotNull(ModResourceFiles.find("assets", "postrider", "textures", "entity", "mochila.png"));
		assertNotNull(ModResourceFiles.find("assets", "postrider", "textures", "entity", "postman.png"));
	}
}
