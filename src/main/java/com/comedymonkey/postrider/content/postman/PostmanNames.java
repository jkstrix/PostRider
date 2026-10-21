package com.comedymonkey.postrider.content.postman;

import java.util.List;

import net.minecraft.util.RandomSource;

/** Old West names for riders and their horses. */
public final class PostmanNames {
	public static final List<String> FIRST_NAMES = List.of(
		"Jesse", "Billy", "Wyatt", "Cody", "Hank", "Silas", "Eli", "Jed", "Clay", "Amos", "Boone", "Cal",
		"Abe", "Gus", "Levi", "Moses", "Ned", "Obadiah", "Rufus", "Virgil", "Zeke", "Walt", "Thaddeus", "Ike",
		"Johnny", "Sam", "Tom", "Will", "Frank", "Jim", "Josiah", "Bart", "Hiram", "Luther", "Emmett", "Wade");
	public static final List<String> SURNAMES = List.of(
		"Carver", "McCoy", "Hollis", "Tate", "Briggs", "Holloway", "Cassidy", "Grady", "Sutter", "Pike", "Colt",
		"Crockett", "Dalton", "Earp", "Fry", "Garrett", "Hardin", "Haskell", "Keogh", "Logan", "Maddox", "Monroe",
		"Prescott", "Quaid", "Rawlins", "Slade", "Thorne", "Whitlock", "Yates", "Barlow", "Cobb", "Dunne");
	public static final List<String> HORSE_NAMES = List.of(
		"Dusty", "Biscuit", "Thunder", "Ginger", "Pepper", "Buckshot", "Chestnut", "Rusty", "Sage", "Comet",
		"Blaze", "Whiskey", "Molasses", "Apache", "Cinnamon", "Domino", "Flapjack", "Jericho", "Lightning",
		"Maverick", "Nutmeg", "Outlaw", "Patches", "Ranger", "Sundance", "Tumbleweed", "Wildfire", "Duke",
		"Bandit", "Calico", "Dixie", "Goldie", "Mesquite", "Scout", "Trigger", "Copper");

	private PostmanNames() {}

	public static String rider(RandomSource random) {
		return FIRST_NAMES.get(random.nextInt(FIRST_NAMES.size())) + " " + SURNAMES.get(random.nextInt(SURNAMES.size()));
	}

	public static String horse(RandomSource random) {
		return HORSE_NAMES.get(random.nextInt(HORSE_NAMES.size()));
	}
}
