# Pony Express: Design

Minecraft 1.21.1, NeoForge 21.1.x. Standalone (no other mods required). All items are server-side vanilla-style items.

## Workflow rules
- Every model is built in Blockbench first (never hand-written JSON imported later) and approved before work continues.
- Every feature ships with unit tests (`src/test/java`, NeoForge `unitTest`).
- Anything uncertain gets asked, not assumed.

## Mailbox
- Style: US post box on a wooden post, door at the front, side flag that goes up when the inbox has mail. Two blocks tall. Only the box takes the dye colour.
- The flag is red on every colour except the red mailbox, which gets a yellow flag so it stands out.
- All 16 dye colours. Recipe: iron ingots around a chest, with a fence underneath (comes out white). Craft with a dye to recolour, or right-click a placed mailbox with a dye.
- Placement is always allowed. If it has no sky view, the placer gets a warning that the postman will probably lose mail.
- Owner types a name in the mailbox screen. Names are 3–24 characters and unique server-wide, ignoring case. A player can own several mailboxes.
- Only the owner can open it, and only the owner (or an operator) can break it.
- Inbox: 27 sealed parcels/letters. When broken, the contents spill on the ground and the name is freed.
- Sending: the mailbox screen has a 9-slot parcel area, an address picked from a list of all mailboxes, and a Send button. Postage is free.

## Parcel and letters
- Parcel item: sealed, holds up to 9 stacks. Right-click to unpack into your inventory (overflow drops at your feet).
- Letters: craft a blank Letter, use it to open its own writing screen, and finish it to get a Written Letter. Using a Written Letter opens it to read. It stays a readable item you can keep.
- Put a Written Letter in the parcel slots to send it. If the parcel holds nothing but one letter, it travels and arrives as just the letter. If there's anything else in it, everything (letter included) arrives inside a Parcel.
- Every screen shares the old-time post office look: walnut panelling, a parchment work surface, brass fittings, postage-stamp tabs, red wax for the main action, and ledger pages for lists (`client/gui/PostOfficeStyle`, `textures/gui/post_office.png`).
- Failure letter: a readable Letter item (book-style page) giving the reason (e.g. "killed by a Zombie"), the Lost Parcel's X/Y/Z and the addressee.

## Postman
- A custom player-like mob with a Pony Express rider skin (wide-brimmed hat, bandana, leather vest), riding a vanilla-style horse with saddlebags at average horse speed (about 9.5 blocks/s).
- Pickup: he appears in a puff of smoke at the sender's mailbox, takes the parcel and summons his horse.
- Delivery: he dismounts, puts the parcel in the inbox and vanishes in smoke with his horse.
- Same dimension only, no distance cap. He loads a 3×3 area of chunks around himself. At most 32 postmen out at once; extra mail waits its turn.
- Prefers land and takes a land detour of up to 2× the straight-line distance. Otherwise he crosses water on a ferry barge (flat barge with railings and a lantern post) that carries him and the horse.
- Takes any damage a player would: mobs, lightning, fall, fire, drowning, and players. Every hostile mob that hunts players targets him, and creepers explode on him.
- If the horse dies, he carries on on foot (still using the barge on water).
- Camps every night (dusk to dawn) in a tent entity model, with no blocks placed. While camped, mobs only notice him within 4 blocks. If night falls on water, he keeps going until he reaches land. If the night is skipped, he packs up in the morning.
- Unstuck: after 30 s with no progress, he teleports himself and his horse/barge up to 16 blocks ahead onto safe ground, or to the surface if he's underground. After 5 tries in a row with no progress, the delivery fails.

## Failure
- Fails when he dies or runs out of unstuck tries.
- Destination mailbox broken or renamed, or inbox full: the Lost Parcel is placed where the mailbox is or was.
- Lost Parcel: a placed block that never despawns. The sender, the recipient, the player who killed the postman, or an operator can open it or break it. Opening it puts its contents in your inventory and removes it. Anyone else who right-clicks it sees who sent it, where it was going and where it came from, but can't take it.
- Its look: a battered, torn, grimy parcel with snapped string and a red LOST tag. If the rider was killed, a skull in his cowboy hat lies beside it. If he gave up after getting stuck, a crumpled map lies there instead.
- The sender gets a failure letter in their mailbox. If their mailbox is gone, the failure goes to chat only.

## Notices
- Recipient: chat message when mail arrives (if online), and the flag goes up.
- Sender: chat message when delivered, and a chat message when the postman fails (plus the letter).

## Build order
1. Mailbox: model → approval → block, colours, naming, screen, inbox, tests.
2. Parcel, letter, Lost Parcel (each model approved first).
3. Postman, horse, barge, tent (each model approved first), then travel, camping, unstuck and failure handling.
