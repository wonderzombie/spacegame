import PlayerInputListener.State.IDLE
import PlayerInputListener.State.MOVING
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.actions.Actions

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

    when (event.keyCode) {
      Keys.UP -> player.addAction(Actions.moveBy(0f, player.speed)).also { this.state = MOVING }
      Keys.RIGHT -> player.addAction(Actions.moveBy(player.speed, 0f)).also { this.state = MOVING }
      Keys.DOWN -> player.addAction(Actions.moveBy(0f, -player.speed)).also { this.state = MOVING }
      Keys.LEFT -> player.addAction(Actions.moveBy(-player.speed, 0f)).also { this.state = MOVING }
      Keys.SPACE -> player.fireMissile(player.x + (player.width / 4), player.top)
    }
    return true
  }

  override fun keyUp(event: InputEvent?, keycode: Int): Boolean {
    event ?: return false
    if (keycode !in movementKeys) return false
    state = IDLE
    player.clearActions()
    return true
  }

}