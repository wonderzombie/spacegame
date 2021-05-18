import PlayerInputListener.State.IDLE
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener

class PlayerInputListener(private val player: SpaceActor) : InputListener() {
  private var state: State = IDLE

  enum class State {
    IDLE,
    MOVING
  }

  private val movementKeys: Set<Int> = setOf(Keys.UP, Keys.RIGHT, Keys.DOWN, Keys.LEFT)

  override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
    event ?: return false
    if (player.hasActions()) return false

    if (keycode in movementKeys || movementKeys.any { Gdx.input.isKeyPressed(it) }) {
      val x = when (keycode) {
        Keys.LEFT -> -player.speed
        Keys.RIGHT -> player.speed
        else -> 0
      }.toFloat()

      val y = when (keycode) {
        Keys.UP -> player.speed
        Keys.DOWN -> -player.speed
        else -> 0
      }.toFloat()
      player.moveBy(x, y)
      state = State.MOVING

      return true
    }

    if (keycode == Keys.SPACE) {
      player.fireMissile(player.x, player.y)
      return true
    }

    return false
  }

  override fun keyUp(event: InputEvent?, keycode: Int): Boolean {
    event ?: return false
    if (keycode !in movementKeys) return false
    state = IDLE
    player.clearActions()
    return true
  }

}