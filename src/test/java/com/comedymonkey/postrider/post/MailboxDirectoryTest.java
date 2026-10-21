package com.comedymonkey.postrider.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

class MailboxDirectoryTest {
	private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final GlobalPos RANCH = GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 64, 10));
	private static final GlobalPos INN = GlobalPos.of(Level.OVERWORLD, new BlockPos(500, 70, -300));
	private static final GlobalPos FORTRESS = GlobalPos.of(Level.NETHER, new BlockPos(0, 40, 0));

	@Test
	void registersAndFindsIgnoringCase() {
		MailboxDirectory directory = new MailboxDirectory();
		assertEquals(MailboxDirectory.Result.OK, directory.register("Steve's Ranch", RANCH, STEVE));
		MailboxDirectory.Entry entry = directory.find("STEVE'S RANCH");
		assertNotNull(entry);
		assertEquals("Steve's Ranch", entry.name());
		assertEquals(RANCH, entry.pos());
		assertEquals(STEVE, entry.owner());
	}

	@Test
	void anotherMailboxCannotTakeTheSameAddress() {
		MailboxDirectory directory = new MailboxDirectory();
		directory.register("Ranch", RANCH, STEVE);
		assertEquals(MailboxDirectory.Result.TAKEN, directory.register("ranch", INN, ALEX));
		assertEquals(RANCH, directory.find("Ranch").pos());
	}

	@Test
	void renamingFreesTheOldAddress() {
		MailboxDirectory directory = new MailboxDirectory();
		directory.register("Ranch", RANCH, STEVE);
		assertEquals(MailboxDirectory.Result.OK, directory.register("Big Ranch", RANCH, STEVE));
		assertNull(directory.find("Ranch"));
		assertNotNull(directory.find("Big Ranch"));
		assertEquals(1, directory.size());
		assertEquals(MailboxDirectory.Result.OK, directory.register("Ranch", INN, ALEX));
	}

	@Test
	void aMailboxCanChangeTheCaseOfItsOwnAddress() {
		MailboxDirectory directory = new MailboxDirectory();
		directory.register("ranch", RANCH, STEVE);
		assertEquals(MailboxDirectory.Result.OK, directory.register("Ranch", RANCH, STEVE));
		assertEquals("Ranch", directory.find("ranch").name());
	}

	@Test
	void rejectsInvalidNames() {
		MailboxDirectory directory = new MailboxDirectory();
		assertEquals(MailboxDirectory.Result.INVALID, directory.register("ab", RANCH, STEVE));
		assertEquals(0, directory.size());
	}

	@Test
	void removingAMailboxFreesItsName() {
		MailboxDirectory directory = new MailboxDirectory();
		directory.register("Ranch", RANCH, STEVE);
		directory.remove(RANCH);
		assertNull(directory.find("Ranch"));
		assertNull(directory.at(RANCH));
	}

	@Test
	void listsOnlyOneDimensionSortedAlphabetically() {
		MailboxDirectory directory = new MailboxDirectory();
		directory.register("zebra Stables", RANCH, STEVE);
		directory.register("Apple Inn", INN, ALEX);
		directory.register("Fortress", FORTRESS, ALEX);
		assertEquals(List.of("Apple Inn", "zebra Stables"), directory.namesIn(Level.OVERWORLD));
		assertEquals(List.of("Fortress"), directory.namesIn(Level.NETHER));
	}

	@Test
	void survivesSavingAndLoading() {
		MailboxDirectory directory = new MailboxDirectory();
		directory.register("Steve's Ranch", RANCH, STEVE);
		directory.register("Fortress", FORTRESS, ALEX);
		var registries = VanillaRegistries.createLookup();
		CompoundTag saved = directory.save(new CompoundTag(), registries);
		MailboxDirectory loaded = MailboxDirectory.load(saved, registries);
		assertEquals(2, loaded.size());
		assertEquals(RANCH, loaded.find("steve's ranch").pos());
		assertEquals(ALEX, loaded.find("Fortress").owner());
	}
}
