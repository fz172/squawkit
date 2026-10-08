package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.SpecField
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TaskSuggestionEntryTest {

  private val template = ThingTemplate(
    id = "car",
    spec_fields = listOf(
      SpecField(key = "make", label = "Make", required = true),
      SpecField(key = "model", label = "Model", required = true),
      SpecField(key = "vin", label = "VIN"),
    ),
  )

  private val complete = Thing(
    id = THING,
    template = template,
    spec = listOf(
      Spec(key = "make", value_ = "Toyota"),
      Spec(key = "model", value_ = "Tacoma")
    ),
  )

  private fun entry(
    guest: Boolean = false,
    thing: Thing = complete,
  ): TaskSuggestionEntry {
    val user = mockk<FirebaseUser> { every { isAnonymous } returns guest }
    val auth =
      mockk<FirebaseAuth> { every { authStateChanged } returns flowOf(user) }
    val fleet =
      mockk<FleetManager> { every { loadThing(THING) } returns flowOf(thing) }
    return TaskSuggestionEntry(
      auth = auth,
      fleetManager = fleet,
      templateRegistry = mockk<TemplateRegistry> {
        every { forThingWithFallback(any()) } answers { firstArg<Thing>().template!! }
      },
    )
  }

  @Test
  fun `is available to a signed-in user on a described Thing`() = runTest {
    assertThat(
      entry().observe(THING)
        .first()
    ).isEqualTo(SuggestEntry.Available)
  }

  @Test
  fun `asks a guest to sign in before anything else`() = runTest {
    assertThat(
      entry(
        guest = true,
        thing = complete.copy(spec = emptyList())
      ).observe(THING)
        .first()
    )
      .isEqualTo(SuggestEntry.SignInRequired)
  }

  @Test
  fun `names the required fields left empty, and only those`() = runTest {
    val noModel = complete.copy(
      spec = listOf(
        Spec(key = "make", value_ = "Toyota"),
        Spec(key = "model", value_ = " ")
      ),
    )

    assertThat(
      entry(thing = noModel).observe(THING)
        .first()
    )
      .isEqualTo(SuggestEntry.MissingIdentity(listOf("Model")))
  }

  @Test
  fun `a template that requires nothing is always described enough`() =
    runTest {
      val custom = Thing(
        id = THING,
        template = ThingTemplate(
          id = "custom",
          spec_fields = listOf(SpecField(key = "notes"))
        ),
      )

      assertThat(
        entry(thing = custom).observe(THING)
          .first()
      ).isEqualTo(SuggestEntry.Available)
    }

  private companion object {
    const val THING = "thing-1"
  }
}
