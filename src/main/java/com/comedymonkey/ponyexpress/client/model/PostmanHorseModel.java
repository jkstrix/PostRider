package com.comedymonkey.ponyexpress.client.model;

import com.comedymonkey.ponyexpress.content.postman.PostmanHorse;

import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelPart;

/** The vanilla horse model, letting the mochila layer see where the body is. */
public class PostmanHorseModel extends HorseModel<PostmanHorse> {
	public PostmanHorseModel(ModelPart root) {
		super(root);
	}

	public ModelPart body() {
		return body;
	}
}
