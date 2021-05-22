import FirstScreen.GameState.GAME_OVER
import FirstScreen.GameState.RUNNING
import FirstScreen.Kind.BOMB
import FirstScreen.Kind.ENEMY
import FirstScreen.Kind.MISSILE
import FirstScreen.Kind.PLAYER
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.utils.Logger
import com.badlogic.gdx.utils.Logger.INFO
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import ktx.actors.then
import ktx.assets.Asset
import ktx.collections.GdxArray
import ktx.graphics.use
import ktx.scene2d.actor
import ktx.scene2d.actors

val logger = Logger("1st", INFO)

/** First screen of the application. Displayed after the application is created.  */
class FirstScreen(
  private val font: Asset<BitmapFont>,
  private val playerShipTex: Asset<Texture>,
  private val enemyShipTex: Asset<Texture>,
  private val missileTex: Asset<Texture>,
  private val bombTex: Asset<Texture>,
) : Screen {
  enum class Kind {
    PLAYER, ENEMY, MISSILE, BOMB;

    companion object {
      val ProjectileKinds = listOf(MISSILE, BOMB)
      val ActorKinds = listOf(PLAYER, ENEMY)
    }
  }

  private val minWorldWidth: Float = 240f
  private val minWorldHeight: Float = 180f

  private val camera = OrthographicCamera()
  private val viewport = ExtendViewport(minWorldWidth, minWorldHeight, camera)
  private val stage = Stage(viewport)

  private val defaultLives: Int = 5
  private val enemyBombInterval: Long = 5 * 1000
  private val numEnemies: Int = 6
  private val enemyBombs: GdxArray<SpaceActor> = GdxArray()
  private val playerMissiles: GdxArray<SpaceActor> = GdxArray()

  private lateinit var player: SpaceActor

  private var gameState: GameState = RUNNING
  private var remainingLives: Int = defaultLives
  private var currentLevel: Int = 1
  private var enemiesDestroyed: Int = 0

  private var lastBombTime: Long = enemyBombInterval * 2


  override fun show() {
    Gdx.input.inputProcessor = stage

    missileTex.finishLoading()
    bombTex.finishLoading()
    playerShipTex.finishLoading()
    enemyShipTex.finishLoading()

    camera.setToOrtho(false, viewport.worldWidth, viewport.worldHeight)
    camera.update()

    stage.actors {
      actor(SpaceActor(playerShipTex, PLAYER, missileTex)).also {
        player = it
        player.x = (minWorldWidth / 2) - (player.width / 2)
      }
    }
    spawnEnemies()
    stage.isDebugAll = true

    check(stage.actors.size > numEnemies)
  }

  private fun enemyPatrol(inc: Float) =
    Actions.forever(
      Actions.sequence(
        Actions.repeat(200, Actions.moveBy(0.05f * inc, 0f)),
        Actions.repeat(200, Actions.moveBy(-0.05f * inc, 0f))
      )
    )

  private fun Actor.getRect(rect: Rectangle): Rectangle =
    rect.set(this.x, this.y, this.width, this.height)


  private val movementKeys = listOf(Keys.LEFT, Keys.RIGHT)

  override fun render(delta: Float) {
    when (gameState) {
      RUNNING -> renderGame(delta)
      GAME_OVER -> renderGameOver(delta)
    }
  }

  private fun renderGameOver(delta: Float) {
    stage.batch.use {
      drawHud(it)
      drawGameOver(it)
    }

    if (Gdx.input.isKeyJustPressed(Keys.ENTER)) {
      gameState = RUNNING
      player.isVisible = true
      spawnEnemies()
      player.clearActions()
    }
  }

  private fun drawGameOver(batch: Batch) {
    font.asset.draw(batch, "GAME OVER", 50f, 100f)
    font.asset.draw(batch, "Press enter to play again", 50f, 100f - font.asset.capHeight)
  }

  private fun renderGame(delta: Float) {
    val actorRect = Rectangle()
    val projectleRect = Rectangle()

    handlePlayerInput()

    val spaceActors: Map<Kind, List<SpaceActor>> =
      stage.actors.filterIsInstance<SpaceActor>().groupBy { it.kind }

    updateProjectiles(spaceActors, projectleRect, actorRect)

    if (spaceActors[ENEMY]?.isEmpty() != false || gameState == GAME_OVER) {
      spaceActors[BOMB]?.onEach { it.remove(); enemyBombs.removeValue(it, true) }
      spaceActors[MISSILE]?.onEach { it.remove(); playerMissiles.removeValue(it, true) }
      if (gameState == GAME_OVER) {
        spaceActors[ENEMY]?.onEach { it.remove() }
        remainingLives = defaultLives
        currentLevel = 1
      } else {
        currentLevel += 1
        spawnEnemies(inc = currentLevel.toFloat())
      }
    }

    with(stage) {
      act(delta)
      draw()
    }

    stage.batch.use {
      drawHud(it)
    }
  }

  private fun handlePlayerInput() {
    movementKeys.find { Gdx.input.isKeyPressed(it) }?.let {
      val baseSpeed = player.width * 3f
      val adjustedSpeed = Gdx.graphics.deltaTime * (if (it == Keys.LEFT) -baseSpeed else baseSpeed)
      player.x += adjustedSpeed
    }

    if (player.x <= 0) {
      player.x = 0f
    } else if (player.right >= minWorldWidth) {
      player.x = minWorldWidth - player.width - 1
    }

    if (Gdx.input.isKeyJustPressed(Keys.SPACE) && playerMissiles.size < 3) {
      val offset = player.width / 2
      playerMissiles.add(player.fireMissile(player.x + offset, player.top))
    }
  }

  private fun updateProjectiles(
    spaceActors: Map<Kind, List<SpaceActor>>,
    projectleRect: Rectangle,
    actorRect: Rectangle
  ) {
    val missileHits = mutableMapOf<SpaceActor, SpaceActor>()
    spaceActors[MISSILE]?.onEach { missile ->
      projectleRect.set(missile.x, missile.y, missile.width, missile.height)
      spaceActors[ENEMY]?.onEach { enemy ->
        actorRect.set(enemy.x, enemy.y, enemy.width, enemy.height)
        if (actorRect.overlaps(projectleRect)) missileHits += missile to enemy
      }
    }

    missileHits.onEach { (missile, target) ->
      missile.isVisible = false
      maybeBlowUp(target)
      cleanUp(missile, target)
    }

    spaceActors[BOMB]?.onEach { bomb ->
      bomb.getRect(projectleRect)
      if (projectleRect.overlaps(player.getRect(actorRect))) {
        enemyBombs.removeValue(bomb, true)
        handlePlayerHit(player, bomb)
      }
    }

    if (remainingLives == 0) {
      handlePlayerLoss(player)
    }

    lastBombTime = maybeDropBomb(lastBombTime)

    cleanUpProjectiles()
  }

  private fun cleanUp(missile: SpaceActor, target: SpaceActor) {
    stage.actors.removeValue(target, true)
    stage.actors.removeValue(missile, true)
    playerMissiles.removeValue(missile, true)
  }

  private fun spawnEnemies(
    count: Int = numEnemies,
    x: Float = 25f,
    y: Float = 100f,
    inc: Float = 1f
  ) {

    stage.actors {
      repeat(count) { i ->
        actor(SpaceActor(enemyShipTex, ENEMY, bomb = bombTex)) {
          setPosition(i * x, y)
          addAction(enemyPatrol(inc))
        }
      }
    }
  }

  private fun drawHud(batch: Batch) {
    font.asset.draw(
      batch,
      "SCORE: ${enemiesDestroyed * 10} | LEVEL: $currentLevel | LIVES: $remainingLives",
      60f,
      minWorldHeight - 5
    )
  }

  private fun handlePlayerHit(player: SpaceActor, bomb: SpaceActor): Boolean {
    enemyBombs.removeValue(bomb, true)
    stage.actors.removeValue(bomb, true)

    remainingLives -= 1
    player.clearActions()
    player.addAction(playerHit())
    return remainingLives == 0
  }

  private fun playerHit() = Actions.repeat(
    5,
    Actions.sequence(Actions.color(Color.RED, 0.1f), Actions.color(Color.WHITE, 0.1f))
  )

  enum class GameState {
    RUNNING,
    NEW_LEVEL,
    GAME_OVER
  }

  private fun handlePlayerLoss(player: SpaceActor) {
    player.isVisible = false
    gameState = GAME_OVER
    currentLevel = 1
  }

  private fun maybeDropBomb(lastBombTime: Long): Long {
    val enemies = stage.actors.filterIsInstance<SpaceActor>().filter { it.kind == ENEMY }
    if (enemies.isEmpty()) return lastBombTime

    val adjustedInterval = enemyBombInterval - (250 * currentLevel)
    if (TimeUtils.timeSinceMillis(lastBombTime) >= adjustedInterval) {
      enemies.random().apply {
        dropBomb(x + (width / 2), y)?.let { enemyBombs.add(it) }
      }
      return TimeUtils.millis()
    }
    return lastBombTime
  }

  private fun cleanUpProjectiles() {
    val projectiles: List<SpaceActor> = stage.actors
      .filterIsInstance<SpaceActor>()
      .filter { it.kind in Kind.ProjectileKinds }

    projectiles.onEach {
      if (it.y > stage.height || it.y < 0) {
        it.remove()
        stage.actors.removeValue(it, true)
        when (it.kind) {
          MISSILE -> playerMissiles.removeValue(it, true)
          BOMB -> enemyBombs.removeValue(it, true)
        }
        logger.info("removed $it ${it.kind}")
      }
    }
  }

  private fun maybeBlowUp(enemy: SpaceActor?) {
    logger.info("maybeBlowUp")
    enemy ?: return
    if (!enemy.isVisible) return

    enemy.clearActions()
    enemiesDestroyed++
    enemy.addAction(
      Actions.addAction(
        Actions.repeat(
          5,
          Actions.rotateBy(5f)
        ).then(
          Actions.alpha(0f, 0.5f, Interpolation.fastSlow)
            .then(Actions.run { enemy.remove() })
        )
      )
    )
  }

  override fun resize(width: Int, height: Int) {
    // Resize your screen here. The parameters represent the new window size.
  }

  override fun pause() {
    // Invoked when your application is paused.
  }

  override fun resume() {
    // Invoked when your application is resumed after pause.
    camera.update()
  }

  override fun hide() {
    // This method is called when another screen replaces this one.
  }

  override fun dispose() {
    // Destroy screen's assets here.
  }
}

