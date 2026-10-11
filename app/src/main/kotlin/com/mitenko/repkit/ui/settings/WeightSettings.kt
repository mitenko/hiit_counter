package com.mitenko.repkit.ui.settings

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mitenko.repkit.data.EntryRepository
import com.mitenko.repkit.data.WeightCodecs
import com.mitenko.repkit.data.WeightUnitDefaults
import com.mitenko.repkit.di.ApplicationScope
import com.mitenko.repkit.domain.WeightConversion
import com.mitenko.repkit.domain.WeightField
import com.mitenko.repkit.domain.WeightMove
import com.mitenko.repkit.domain.WeightValidation
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.domain.resolveWeightEdit
import com.mitenko.repkit.domain.weightValidation
import com.mitenko.repkit.ui.common.AutoSaver
import com.mitenko.repkit.ui.common.EntryScopedViewModel
import com.mitenko.repkit.ui.common.SaveStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a weight-mode edit moved (plan Spec notes 30–31), shown under [at]; null is Reset to defaults. */
data class WeightNote(val at: WeightField?, val moves: List<WeightMove>)

/**
 * The weight half of the Progression page (spec rev 26 §3, plan Spec note 25): the mode and the weight
 * group, saved through setWeightConfig with the R3 pipeline (steppers 400 ms after the last change,
 * everything else at once, invalid drafts never). An edit wins and the values it pushes move to fit
 * (revision 28 §6); [note] says what moved, and a current load the save moved is appended when it lands.
 * While the pager is open, a draft without unsaved edits follows the store.
 */
@HiltViewModel
class WeightSettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    private val unitDefaults: WeightUnitDefaults,
    @ApplicationScope private val appScope: CoroutineScope,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val _mode = MutableStateFlow<ProgressMode?>(null)

    /** The stored Progress by mode; null until loaded. */
    val mode: StateFlow<ProgressMode?> = _mode.asStateFlow()

    private val _draft = MutableStateFlow(savedStateHandle.restoredWeightDraft())
    val draft: StateFlow<WeightConfig?> = _draft.asStateFlow()

    val validation: StateFlow<WeightValidation> = combine(_draft, _mode) { d, m ->
        if (d == null || m == null) WeightValidation() else weightValidation(d, m)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeightValidation())

    private val failed = MutableStateFlow(false)
    val status: StateFlow<SaveStatus> = combine(validation, failed) { v, f -> SaveStatus.of(v.isValid, f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SaveStatus.SAVED)

    private val _note = MutableStateFlow<WeightNote?>(null)

    /** What the last edit moved, until the next edit or a page change ([clearNote]). */
    val note: StateFlow<WeightNote?> = _note.asStateFlow()

    /** Advanced by [clearNote]; a save only adds to the note if this hasn't moved since it was queued (revision 28 §4). */
    private var noteGeneration = 0L

    /** Where a current load moved by a save is noted: the last edited field, or null after Reset to defaults. */
    private var noteAnchor: WeightField? = null

    private val _unitPrompt = MutableStateFlow<WeightUnit?>(null)

    /** The unit the user asked to switch to, waiting for the confirm (plan Spec note 34). */
    val unitPrompt: StateFlow<WeightUnit?> = _unitPrompt.asStateFlow()

    private val _modePrompt = MutableStateFlow<ProgressMode?>(null)

    /** The mode the user picked under Progress by, waiting for the Start fresh confirm (spec rev 26 §2). */
    val modePrompt: StateFlow<ProgressMode?> = _modePrompt.asStateFlow()

    /** A queued weight write and the note generation it was queued in. */
    private data class Save(val config: WeightConfig, val generation: Long)

    private val saver = AutoSaver<Save>(viewModelScope) { (config, generation) ->
        try {
            val moved = repo.setWeightConfig(entryId, config)
            if (moved != null && noteGeneration == generation) {
                _note.value = WeightNote(noteAnchor, _note.value?.moves.orEmpty() + moved)
            }
            failed.value = false
        } catch (e: EntryNotFound) {
            markMissing()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Weights $config rejected", e)
            failed.value = true
        }
    }

    /** The weight group as last stored. A draft equal to it, with no save pending, has no unsaved edits. */
    private var stored: WeightConfig? = null

    init {
        val restored = _draft.value
        viewModelScope.launch {
            repo.entry(entryId).filterNotNull().collect { e ->
                val mode = e.progression.mode
                val latest = e.progression.weight
                _mode.value = mode
                // A valid draft restored after process death may never have been written. Its validity
                // depends on the mode, so it's checked on the first emission; the write is idempotent.
                if (stored == null && restored != null && restored != latest && weightValidation(restored, mode).isValid) {
                    saver.schedule(Save(restored, noteGeneration))
                }
                val current = _draft.value
                if (current == null || (current == stored && !saver.hasPending)) setDraft(latest)
                stored = latest
            }
        }
    }

    /** A stepper change to [field]: saved 400 ms after the last one. */
    fun update(field: WeightField?, transform: (WeightConfig) -> WeightConfig) = edit(field, transform, now = false)

    /** A dialog OK, a pick, a choice, ✕ or "+ Add": saved at once. */
    fun updateNow(field: WeightField?, transform: (WeightConfig) -> WeightConfig) = edit(field, transform, now = true)

    /** kg | lb: a different unit asks first (plan Spec note 34). */
    fun requestUnit(unit: WeightUnit) {
        if (unit != _draft.value?.unit) _unitPrompt.value = unit
    }

    /** Convert: the draft converts (Steps become My weights, §10 note 8) and saves at once. */
    fun confirmUnit() {
        val to = _unitPrompt.value ?: return
        _unitPrompt.value = null
        updateNow(WeightField.UNIT) { WeightConversion.convert(it, to) }
    }

    fun dismissUnitPrompt() {
        _unitPrompt.value = null
    }

    /** Progress by: a different mode asks "Start fresh?" first (spec rev 26 §2 Switching mode). */
    fun requestMode(to: ProgressMode) {
        if (to != _mode.value) _modePrompt.value = to
    }

    fun dismissModePrompt() {
        _modePrompt.value = null
    }

    /**
     * Start fresh (plan Spec note 33, §10 notes 5 and 13): the pending weight write lands first, then
     * switchMode runs with the app default unit, then the draft takes the stored weight group, dropping
     * unsaved edits. The caller flushes the Progression page's own draft before this. Run in [appScope],
     * like Reset progress, so leaving the page can't drop the switch.
     */
    fun confirmMode() {
        val to = _modePrompt.value ?: return
        _modePrompt.value = null
        clearNote()
        saver.flush()
        appScope.launch {
            try {
                val unit = unitDefaults.weightUnitDefault.first()
                saver.exclusive { repo.switchMode(entryId, to, unit) }
                viewModelScope.launch {
                    repo.entry(entryId).first()?.let { e ->
                        _mode.value = e.progression.mode
                        stored = e.progression.weight
                        setDraft(e.progression.weight)
                        failed.value = false
                    }
                }
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }

    /** Reset to defaults in a weight mode (open question 1): the weight group's defaults, keeping the unit; saved at once. */
    fun resetToDefaults() {
        noteAnchor = null
        updateNow(null) { WeightConfig(unit = it.unit) }
    }

    fun flush() = saver.flush()

    /** The next edit or a page change hides the note. */
    fun clearNote() {
        noteGeneration++
        _note.value = null
    }

    override fun onCleared() {
        saver.flushIn(appScope)
    }

    private fun edit(field: WeightField?, transform: (WeightConfig) -> WeightConfig, now: Boolean) {
        val before = _draft.value ?: return
        val mode = _mode.value ?: return
        val resolution = resolveWeightEdit(mode, before, transform(before), field)
        clearNote()
        if (field != null) noteAnchor = field
        if (resolution.moves.isNotEmpty()) _note.value = WeightNote(field, resolution.moves)
        val config = resolution.config
        setDraft(config)
        when {
            !weightValidation(config, mode).isValid -> saver.cancel()
            now -> saver.saveNow(Save(config, noteGeneration))
            else -> saver.schedule(Save(config, noteGeneration))
        }
    }

    private fun setDraft(d: WeightConfig) {
        _draft.value = d
        savedStateHandle[INTS_KEY] = intArrayOf(
            d.unit?.ordinal ?: -1, d.kind.ordinal, d.repsPerSet, d.repMin, d.repMax, d.startWeight ?: -1, d.startReps ?: -1,
        )
        savedStateHandle[STEPS_KEY] = WeightCodecs.encodeSteps(d.steps)
        savedStateHandle[LIST_KEY] = WeightCodecs.encodeList(d.list)
        savedStateHandle[HOLDS_KEY] = WeightCodecs.encodeHolds(d.holds)
    }

    private companion object {
        const val TAG = "WeightSettings"
        const val INTS_KEY = "weight_draft"
        const val STEPS_KEY = "weight_draft_steps"
        const val LIST_KEY = "weight_draft_list"
        const val HOLDS_KEY = "weight_draft_holds"

        fun SavedStateHandle.restoredWeightDraft(): WeightConfig? {
            val a = get<IntArray>(INTS_KEY) ?: return null
            val steps = get<String>(STEPS_KEY)?.let(WeightCodecs::decodeSteps) ?: return null
            val list = get<String>(LIST_KEY)?.let(WeightCodecs::decodeList) ?: return null
            val holds = get<String>(HOLDS_KEY)?.let(WeightCodecs::decodeHolds) ?: return null
            return WeightConfig(
                unit = WeightUnit.entries.getOrNull(a[0]),
                kind = WeightsKind.entries[a[1]],
                steps = steps,
                list = list,
                repsPerSet = a[2],
                repMin = a[3],
                repMax = a[4],
                startWeight = a[5].takeIf { it >= 0 },
                startReps = a[6].takeIf { it >= 0 },
                holds = holds,
            )
        }
    }
}
