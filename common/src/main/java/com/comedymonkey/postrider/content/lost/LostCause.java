package com.comedymonkey.postrider.content.lost;

import net.minecraft.util.StringRepresentable;

/** Why a parcel was lost, which also decides what lies beside it. */
public enum LostCause implements StringRepresentable {
	/** The rider was killed: his skull, still in its hat, lies beside the parcel. */
	KILLED("killed"),
	/** The rider got stuck and turned back: his crumpled map lies beside it. */
	STUCK("stuck"),
	/** The rider got there but the mailbox was gone or full: just the parcel. */
	UNDELIVERABLE("undeliverable");

	private final String name;

	LostCause(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
