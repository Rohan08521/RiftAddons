package com.riftaddons.module

import com.riftaddons.util.helper.Clock
import com.riftaddons.util.KeyBindingUtils
import org.cobalt.api.module.setting.impl.KeyBindSetting
import org.cobalt.api.util.ChatUtils
import org.cobalt.api.util.MouseUtils
import org.cobalt.api.module.Module
import org.cobalt.api.util.helper.KeyBind
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.client.TickEvent
import org.cobalt.api.event.impl.client.PacketEvent
import org.cobalt.api.event.impl.render.WorldRenderEvent
import org.cobalt.api.util.render.Render3D
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import org.cobalt.api.util.InventoryUtils
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import java.awt.Color
import kotlin.math.abs
import org.lwjgl.glfw.GLFW
import org.cobalt.api.rotation.RotationExecutor
import org.cobalt.api.rotation.strategy.TimedEaseStrategy
import org.cobalt.api.rotation.EasingType
import org.cobalt.api.util.AngleUtils
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.random.Random
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import org.cobalt.api.util.helper.Rotation

object BerberisMacro : Module(
  name = "Berberis Macro",
) {
  val keyBind by KeyBindSetting(
    name = "Toggle Keybind",
    description = "The keybind to toggle the Berberis Macro.",
    defaultValue = KeyBind(GLFW.GLFW_KEY_G)
  )
  private var isToggled = false
  private var wasKeyPressed = false
  private val mc = Minecraft.getInstance()
  private val clock = Clock()
  private var macroState = MacroState.IDLE
  private var targetBerberis: BlockPos? = null

  private val detectedBerberis = mutableMapOf<BlockPos, Long>()
  private const val BERBERIS_TIMEOUT_MS = 500L

  private val droppedDeadBushes = mutableSetOf<BlockPos>()
  private var shouldPickupDrops = false

  fun start() {
    isToggled = true
    MouseUtils.ungrabMouse()
    macroState = MacroState.SWAP_TO_WAND
    clock.schedule(50)
  }

  fun stop() {
    isToggled = false
    MouseUtils.grabMouse()
    resetStates()
  }

  fun resetStates() {
    detectedBerberis.clear()
    droppedDeadBushes.clear()
    targetBerberis = null
    shouldPickupDrops = false
    KeyBindingUtils.releaseAll()
    macroState = MacroState.SWAP_TO_WAND
  }

  @SubscribeEvent
  fun keybindListener(event: TickEvent) {
    val isPressed = keyBind.isPressed()
    if (isPressed && !wasKeyPressed) {
      isToggled = !isToggled

      if (isToggled) start()
      else stop()

      ChatUtils.sendMessage(
        "Berberis Macro is now "
          + (if (isToggled) "§aEnabled" else "§cDisabled")
          + "§r"
      )
    }
    wasKeyPressed = isPressed
  }

  fun isToggled(): Boolean {
    return isToggled
  }
  fun swapToWand() {
    InventoryUtils.holdHotbarSlot(InventoryUtils.findItemInHotbar("Wand of Farming"))
  }
  enum class MacroState {
    IDLE,
    SWAP_TO_WAND,
    WALK_TO_BERBERY,
    BREAK_BERBERY,
    PICKUP_DROPS,
    RESETTING
  }

  @SubscribeEvent
  fun onTick(event: TickEvent){
    val currentTime = System.currentTimeMillis()
    detectedBerberis.entries.removeIf { currentTime - it.value > BERBERIS_TIMEOUT_MS }

    if (!isToggled) {
      return
    }

    when (macroState) {
      MacroState.SWAP_TO_WAND -> {
        if (clock.passed()) {
          swapToWand()
          macroState = MacroState.WALK_TO_BERBERY
        }
      }
      MacroState.WALK_TO_BERBERY -> {
        walkToBerbery()
      }
      MacroState.BREAK_BERBERY -> {
        targetBerberis?.let { berberis ->
          val targetVec = Vec3(
            berberis.x + 0.5,
            berberis.y + 0.5,
            berberis.z + 0.5
          )
          rotateToDetectedBerberis(targetVec)
        }
        if (clock.passed()) {
          MouseUtils.leftClick()
          macroState = MacroState.PICKUP_DROPS
          clock.schedule(500)
        }
      }
      MacroState.PICKUP_DROPS -> {
        if (clock.passed()) {
          pickupDrops()
        }
      }
      MacroState.RESETTING -> {
        if (clock.passed()) {
          macroState = MacroState.WALK_TO_BERBERY
        }
      }
      MacroState.IDLE -> {
      }
    }
  }

  @SubscribeEvent
  fun onPacketReceive(event: PacketEvent.Incoming) {
    val packet = event.packet
    if (packet is ClientboundLevelParticlesPacket) {
      val level = mc.level ?: return
      val player = mc.player ?: return

      val particleX = packet.x
      val particleY = packet.y
      val particleZ = packet.z

      val particleBlockPos = BlockPos.containing(particleX, particleY, particleZ)

      if (packet.yDist > 0.1f) {
        for (yOffset in 0 downTo -1) {
          val checkPos = particleBlockPos.offset(0, yOffset, 0)
          if (level.getBlockState(checkPos).`is`(Blocks.DEAD_BUSH)) {
            droppedDeadBushes.add(checkPos)
            break
          }
        }
        return
      }

      for (yOffset in 0 downTo -2) {
        val checkPos = particleBlockPos.offset(0, yOffset, 0)

        if (level.getBlockState(checkPos).`is`(Blocks.DEAD_BUSH)) {
          val playerPos = player.position()
          val distanceX = checkPos.x + 0.5 - playerPos.x
          val distanceY = checkPos.y + 0.5 - playerPos.y
          val distanceZ = checkPos.z + 0.5 - playerPos.z
          val distanceToPlayer = sqrt(distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ)

          if (distanceToPlayer > 15.0) continue

          val blockCenterX = checkPos.x + 0.5
          val blockCenterZ = checkPos.z + 0.5

          val dx = particleX - blockCenterX
          val dz = particleZ - blockCenterZ

          if (abs(dx) <= 0.3 && abs(dz) <= 0.3) {
            detectedBerberis[checkPos] = System.currentTimeMillis()
            break
          }
        }
      }
    }
  }

  @SubscribeEvent
  fun onWorldRender(event: WorldRenderEvent.Last) {
    val currentTime = System.currentTimeMillis()


    for ((pos, detectionTime) in detectedBerberis) {
      val age = currentTime - detectionTime
      val pulseSpeed = 0.003f
      val alpha = 0.5f + 0.3f * sin(age * pulseSpeed)

      val color = if (pos == targetBerberis) {
        Color(255, 0, 255, (alpha * 255).toInt()) // Magenta for target
      } else {
        Color(255, 255, 0, (alpha * 255).toInt()) // Yellow for others
      }

      val box = AABB(
        pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
        pos.x.toDouble() + 1.0, pos.y.toDouble() + 1.0, pos.z.toDouble() + 1.0
      )
      Render3D.drawBox(event.context, box, color, esp = true)
    }


    val level = mc.level ?: return
    val nearbyItems = level.entitiesForRendering()
      .filterIsInstance<ItemEntity>()
      .filter { item ->
        val itemStack = item.item
        itemStack.item == Items.DEAD_BUSH
      }

    for (item in nearbyItems) {
      val itemPos = item.position()
      val pulseSpeed = 0.004f
      val alpha = 0.6f + 0.4f * sin(currentTime * pulseSpeed)
      val color = Color(0, 255, 0, (alpha * 255).toInt()) // Green

      val box = AABB(
        itemPos.x - 0.25, itemPos.y - 0.25, itemPos.z - 0.25,
        itemPos.x + 0.25, itemPos.y + 0.25, itemPos.z + 0.25
      )
      Render3D.drawBox(event.context, box, color, esp = true)

      val innerBox = AABB(
        itemPos.x - 0.15, itemPos.y - 0.15, itemPos.z - 0.15,
        itemPos.x + 0.15, itemPos.y + 0.15, itemPos.z + 0.15
      )
      Render3D.drawBox(event.context, innerBox, Color(100, 255, 100, 150), esp = true)
    }

    if (isToggled) {
      val player = mc.player ?: return
      val playerPos = player.position()

      val stateColor = when (macroState) {
        MacroState.SWAP_TO_WAND -> Color(200, 200, 200, 100) // White
        MacroState.WALK_TO_BERBERY -> Color(0, 200, 255, 100) // Cyan
        MacroState.BREAK_BERBERY -> Color(255, 100, 0, 150) // Orange
        MacroState.PICKUP_DROPS -> Color(0, 255, 100, 150) // Bright Green
        MacroState.RESETTING -> Color(255, 255, 0, 100) // Yellow
        MacroState.IDLE -> Color(100, 100, 100, 80) // Gray
      }


      val stateBox = AABB(
        playerPos.x - 0.3, playerPos.y - 0.05, playerPos.z - 0.3,
        playerPos.x + 0.3, playerPos.y + 0.05, playerPos.z + 0.3
      )
      Render3D.drawBox(event.context, stateBox, stateColor, esp = true)
    }
  }


  fun walkToBerbery() {
    val player = mc.player ?: return

    if (detectedBerberis.isEmpty()) {
      KeyBindingUtils.releaseAll()
      targetBerberis = null
      return
    }

    val playerPos = player.position()
    val nearestBerberis = detectedBerberis.keys.minByOrNull { pos ->
      val dx = pos.x + 0.5 - playerPos.x
      val dy = pos.y + 0.5 - playerPos.y
      val dz = pos.z + 0.5 - playerPos.z
      sqrt(dx * dx + dy * dy + dz * dz)
    } ?: return

    targetBerberis = nearestBerberis

    val targetVec = Vec3(
      nearestBerberis.x + 0.5,
      nearestBerberis.y + 0.5,
      nearestBerberis.z + 0.5
    )

    val dx = targetVec.x - playerPos.x
    val dy = targetVec.y - playerPos.y
    val dz = targetVec.z - playerPos.z
    val distance = sqrt(dx * dx + dy * dy + dz * dz)

    rotateToDetectedBerberis(targetVec)

    if (distance <= 2.0) {
      KeyBindingUtils.releaseAll()
      macroState = MacroState.BREAK_BERBERY
      clock.schedule(150)
      return
    }

    val rotation = AngleUtils.getRotation(targetVec)
    val yawDiff = rotation.yaw - player.yRot
    val movementState = KeyBindingUtils.calculateMovement(yawDiff, shouldSprint = true)
    KeyBindingUtils.applyMovement(movementState)
  }

  fun rotateToDetectedBerberis(targetVec: Vec3) {
    val rotation = AngleUtils.getRotation(targetVec)

    if (!RotationExecutor.isRotating()) {
      RotationExecutor.rotateTo(rotation, TimedEaseStrategy(EasingType.LINEAR, EasingType.LINEAR, 250))
    }
  }

  fun pickupDrops() {
    val player = mc.player ?: return
    val level = mc.level ?: return

    // Find nearby dropped item entities (dead bush items on ground)
    val nearbyItems = level.entitiesForRendering()
      .filterIsInstance<ItemEntity>()
      .filter { item ->
        val itemStack = item.item
        itemStack.item == Items.DEAD_BUSH
      }
      .filter { item ->
        val itemPos = item.position()
        val playerPos = player.position()
        val dx = itemPos.x - playerPos.x
        val dy = itemPos.y - playerPos.y
        val dz = itemPos.z - playerPos.z
        sqrt(dx * dx + dy * dy + dz * dz) <= 15.0
      }

    if (nearbyItems.isEmpty()) {
      droppedDeadBushes.clear()
      shouldPickupDrops = false
      macroState = MacroState.RESETTING
      clock.schedule(Random.nextInt(400, 600))
      KeyBindingUtils.releaseAll()
      return
    }

    val playerPos = player.position()
    val nearestItem = nearbyItems.minByOrNull { item ->
      val itemPos = item.position()
      val dx = itemPos.x - playerPos.x
      val dy = itemPos.y - playerPos.y
      val dz = itemPos.z - playerPos.z
      sqrt(dx * dx + dy * dy + dz * dz)
    } ?: return

    val targetVec = nearestItem.position()
    val dx = targetVec.x - playerPos.x
    val dy = targetVec.y - playerPos.y
    val dz = targetVec.z - playerPos.z
    val distance = sqrt(dx * dx + dy * dy + dz * dz)

    // Create rotation but keep pitch at 0 (look straight ahead, not down)
    val yaw = Math.toDegrees(atan2(dz, dx)).toFloat() - 90f
    val rotation = Rotation(yaw, 0f)

    if (!RotationExecutor.isRotating()) {
      RotationExecutor.rotateTo(rotation, TimedEaseStrategy(EasingType.LINEAR, EasingType.LINEAR, 100))
    }

    if (distance <= 1.3) {
      KeyBindingUtils.releaseAll()
      clock.schedule(100)
      macroState = MacroState.RESETTING
      return
    }

    val yawDiff = rotation.yaw - player.yRot
    val movementState = KeyBindingUtils.calculateMovement(yawDiff, shouldSprint = true)
    KeyBindingUtils.applyMovement(movementState)
  }

  fun detectWiltedBerbery(): Set<BlockPos> {
    return detectedBerberis.keys.toSet()
  }
}
