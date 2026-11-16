package com.nisovin.magicspells.spells.instant;

import java.lang.reflect.*;
import java.util.*;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.*;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API-only fixture (as LevitateSpellTest): the spell is allocated without the plugin bootstrap and its map is set
 * directly. Covers that a thrown entity leaving the world drops its map entry, and that turnOff still removes every
 * tracked entity while those removals fire the same event.
 */
class ThrowBlockSpellTrackingTest {

	ThrowBlockSpell spell;
	Map<Entity, Object> fallingBlocks;
	Listener removedListener;
	Method onRemove;
	final World world = proxy(World.class, (o, m, a) -> switch (m.getName()) {
		case "equals" -> o == a[0];
		case "hashCode" -> System.identityHashCode(o);
		default -> null;
	});

	@BeforeEach
	void setup() throws Exception {
		spell = allocate(ThrowBlockSpell.class);
		fallingBlocks = new HashMap<>();
		set(ThrowBlockSpell.class, spell, "fallingBlocks", fallingBlocks);
		ThrowBlockSpell.thrownBlocks = new ArrayList<>();

		Class<?> type = Class.forName(ThrowBlockSpell.class.getName() + "$RemovedEntityListener");
		Constructor<?> constructor = type.getDeclaredConstructor(ThrowBlockSpell.class);
		constructor.setAccessible(true);
		removedListener = (Listener) constructor.newInstance(spell);
		onRemove = type.getDeclaredMethod("onRemove", EntityRemoveFromWorldEvent.class);
		onRemove.setAccessible(true);
	}

	@Test
	void anEntityLeavingTheWorldDropsItsEntry() throws Exception {
		Block thrown = new Block();
		Block other = new Block();
		fallingBlocks.put(thrown.entity, info());
		fallingBlocks.put(other.entity, info());

		fire(thrown.entity);

		assertFalse(fallingBlocks.containsKey(thrown.entity));
		assertTrue(fallingBlocks.containsKey(other.entity), "other thrown blocks stay tracked");
	}

	@Test
	void anUntrackedEntityLeavingTheWorldChangesNothing() throws Exception {
		Block thrown = new Block();
		fallingBlocks.put(thrown.entity, info());

		fire(new Block().entity);

		assertEquals(Set.of(thrown.entity), fallingBlocks.keySet());
	}

	@Test
	void turnOffRemovesEveryEntityWhileRemovalsFireTheEvent() throws Exception {
		List<Block> blocks = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			Block b = new Block();
			b.fireOnRemove = true;
			blocks.add(b);
			fallingBlocks.put(b.entity, info());
		}

		assertDoesNotThrow(() -> spell.turnOff());

		for (Block b : blocks)
			assertEquals(1, b.removes);
		assertTrue(fallingBlocks.isEmpty());
	}

	void fire(Entity entity) throws Exception {
		onRemove.invoke(removedListener, new EntityRemoveFromWorldEvent(entity, world));
	}

	Object info() throws Exception {
		return allocate(ThrowBlockSpell.ThrownBlock.class);
	}

	/** A falling block whose remove() can fire EntityRemoveFromWorldEvent synchronously, as Paper does. */
	class Block {
		int removes;
		boolean fireOnRemove;
		final FallingBlock entity = proxy(FallingBlock.class, (o, m, a) -> switch (m.getName()) {
			case "equals" -> o == a[0];
			case "hashCode" -> System.identityHashCode(o);
			case "toString" -> "Block@" + System.identityHashCode(o);
			case "remove" -> {
				removes++;
				if (fireOnRemove) fire((Entity) o);
				yield null;
			}
			default -> throw new AssertionError(m);
		});
	}

	static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}

	static <T> T allocate(Class<T> type) throws Exception {
		Field f = Unsafe.class.getDeclaredField("theUnsafe");
		f.setAccessible(true);
		return type.cast(((Unsafe) f.get(null)).allocateInstance(type));
	}

	static void set(Class<?> type, Object owner, String name, Object value) throws Exception {
		Field f = type.getDeclaredField(name);
		f.setAccessible(true);
		f.set(owner, value);
	}

}
