package com.giyeok.passzero2.gui.entries

import com.giyeok.passzero2.core.StorageProto
import com.giyeok.passzero2.core.storage.StorageSession
import com.giyeok.passzero2.gui.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class EntryListView(
  private val config: Config,
  private val session: StorageSession,
  initialDirectory: String
) : JPanel() {
  private val state = EntryListViewState(
    config.executors,
    MutableStateFlow(null),
    DefaultComboBoxModel(),
    initialDirectory,
    false,
    MutableStateFlow(null),
    mutableListOf(),
    "",
    DefaultListModel(),
    EntryDetailState.EmptyDetails(true)
  )
  private val stateMutex = Mutex()

  /**
   * 의미적으로는 stateFlow가 MutableSharedFlow<EntryListViewState>(0) 이어도 되는데, 이상하게 setState에서
   * emit이 멈추지 않거나 tryEmit으로 바꾸면 emit이 실패하는 현상이 생겨서 replay=1로 주고 BufferOverflow를 SUSPEND가 아닌 것으로 바꿈
   */
  private val stateFlow =
    MutableSharedFlow<EntryListViewState>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val directoryCombo = JComboBox(state.directoryList)
  private val refreshButton = JButton(ImageIcon(javaClass.getResource("/icons8-refresh-30.png")))

  private val list = JList(state.filteredEntries)
  private val searchTextField = JTextField()
  private val entriesCountLabel = JLabel()
  private val detailScroll = JScrollPane()
  private val detailMenus = JPanel()

  init {
    initView(state)
    // TODO resource leak on `launch`es?
    CoroutineScope(config.executors.asCoroutineDispatcher()).launch {
      stateFlow.map { it.entryDetailState }.distinctUntilChanged().collectLatest { state ->
        updateDetailsView(state)
      }
    }

    val filteredEntriesCountFlow = callbackFlow {
      state.filteredEntries.addListDataListener(object : ListDataListener {
        override fun intervalAdded(e: ListDataEvent?) {
          trySend(state.filteredEntries.size)
        }

        override fun intervalRemoved(e: ListDataEvent?) {
          trySend(state.filteredEntries.size)
        }

        override fun contentsChanged(e: ListDataEvent?) {
          trySend(state.filteredEntries.size)
        }
      })
      awaitClose()
    }

    CoroutineScope(config.executors.asCoroutineDispatcher()).launch {
      filteredEntriesCountFlow.zip(stateFlow.map { it.entryList.size }, ::Pair)
        .distinctUntilChanged()
        .collectLatest {
          SwingUtilities.invokeLater {
            if (state.filterText.isEmpty()) {
              entriesCountLabel.text = "${state.entryList.size}"
            } else {
              entriesCountLabel.text = "${state.filteredEntries.size}/${state.entryList.size}"
            }
          }
        }
    }

    CoroutineScope(config.executors.asCoroutineDispatcher()).launch {
      stateFlow.map { it.regeneratingCache }.distinctUntilChanged()
        .collectLatest { regeneratingCache ->
          SwingUtilities.invokeLater {
            refreshButton.isEnabled = !regeneratingCache
          }
        }
    }
  }

  private fun setState(stateUpdater: EntryListViewState.() -> Unit) {
    runBlocking {
      stateMutex.withLock {
        stateUpdater(state)
      }
      stateFlow.emit(state)
    }
  }

  private fun initView(state: EntryListViewState) {
    layout = BorderLayout()

    directoryCombo.font = config.bigFont
    directoryCombo.renderer = ListCellRenderer { _, value, _, _, _ ->
      JLabel(value?.name ?: "???")
    }

    list.cellRenderer = ListCellRenderer { _, value, _, isSelected, _ ->
      val label = JLabel(value.info.name)
      label.font = config.defaultFont
      if (isSelected) {
        label.foreground = Color.BLACK
        label.background = Color.LIGHT_GRAY
        label.isOpaque = true
        label.border = BorderFactory.createLineBorder(Color.RED)
      } else {
        label.border = BorderFactory.createLineBorder(Color.WHITE)
      }
      label
    }
    list.layoutOrientation = JList.VERTICAL

    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.addListSelectionListener { event ->
      when (state.entryDetailState) {
        is EntryDetailState.EditingEntry, is EntryDetailState.CreatingEntry -> {
        }
        is EntryDetailState.ShowingDetails, is EntryDetailState.EmptyDetails -> {
          if (!event.valueIsAdjusting) {
            if (list.selectedIndex >= 0 && list.selectedIndex < state.filteredEntries.size) {
              val entry = state.filteredEntries[list.selectedIndex]
              setState {
                state.entryDetailState = EntryDetailState.ShowingDetails(entry, true)
              }
            } else {
              setState {
                state.entryDetailState = EntryDetailState.EmptyDetails(true)
              }
            }
          }
        }
      }
    }

    val listScroll = JScrollPane()
    listScroll.setViewportView(list)

    val leftTopPanel = JPanel()
    leftTopPanel.layout = GridBagLayout()
    val sessionInfoButton = JButton(ImageIcon(javaClass.getResource("/icons8-info-30.png")))
    var gbc = GridBagConstraints()
    gbc.gridx = 0
    gbc.gridy = 0
    sessionInfoButton.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        super.mouseClicked(e)
      }
    })
    leftTopPanel.add(sessionInfoButton, gbc)

    gbc = GridBagConstraints()
    gbc.gridx = 1
    gbc.gridy = 0
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.weightx = 1.0
    leftTopPanel.add(directoryCombo, gbc)

    gbc = GridBagConstraints()
    gbc.gridx = 2
    gbc.gridy = 0
    refreshButton.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (!state.regeneratingCache) {
          if (e.clickCount >= 2) {
            setState {
              state.regeneratingCache = true
              CoroutineScope(config.executors.asCoroutineDispatcher()).launch {
                val stream = session.createEntryListCacheStreaming(directory).onCompletion {
                  setState {
                    state.regeneratingCache = false
                  }
                }
                state.emitEntryListUpdater(stream)
              }
            }
          } else {
            // TODO 캐시만 새로 읽도록 수정
          }
        }
      }
    })
    leftTopPanel.add(refreshButton, gbc)

    val leftBottomPanel = JPanel()
    leftBottomPanel.layout = GridBagLayout()

    searchTextField.font = config.defaultFont
    gbc = GridBagConstraints()
    gbc.gridx = 0
    gbc.gridy = 0
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.weightx = 1.0
    searchTextField.addKeyListener(object : KeyAdapter() {
      override fun keyReleased(e: KeyEvent?) {
        setState {
          state.filterText = searchTextField.text
        }
      }
    })
    leftBottomPanel.add(searchTextField, gbc)

    entriesCountLabel.font = config.defaultFont
    entriesCountLabel.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        if (searchTextField.text.isNotEmpty()) {
          searchTextField.text = ""
          setState {
            state.filterText = ""
          }
        }
      }
    })
    gbc = GridBagConstraints()
    gbc.gridx = 1
    gbc.gridy = 0
    leftBottomPanel.add(entriesCountLabel, gbc)

    val newSheetButton = JButton(config.getString("NEW_ENTRY"))
    newSheetButton.font = config.defaultFont
    gbc = GridBagConstraints()
    gbc.gridx = 2
    gbc.gridy = 0
    newSheetButton.addActionListener {
      val lastSelection = list.selectedValue
      setState {
        state.entryDetailState = EntryDetailState.CreatingEntry(lastSelection)
      }
    }
    leftBottomPanel.add(newSheetButton, gbc)

    val leftPanel = JPanel()
    leftPanel.layout = BorderLayout()
    leftPanel.add(leftTopPanel, BorderLayout.NORTH)
    leftPanel.add(listScroll, BorderLayout.CENTER)
    leftPanel.add(leftBottomPanel, BorderLayout.SOUTH)

    detailMenus.layout = FlowLayout(FlowLayout.TRAILING)

    val rightPanel = JPanel()
    rightPanel.layout = BorderLayout()
    rightPanel.add(detailScroll, BorderLayout.CENTER)
    rightPanel.add(detailMenus, BorderLayout.SOUTH)

    val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel)
    splitPane.dividerLocation = 400
    splitPane.dividerSize = 5
    splitPane.resizeWeight = 0.5
    add(splitPane, BorderLayout.CENTER)

    setState {
      state.emitDirectoryListUpdater(session.streamDirectoryList())

      CoroutineScope(config.executors.asCoroutineDispatcher()).launch {
        val cached = session.getEntryListCache(directory)
        if (cached != null) {
          setState {
            state.emitEntryListUpdater(flowOf(*cached.entriesList.toTypedArray()))
          }
        } else {
          setState {
            CoroutineScope(config.executors.asCoroutineDispatcher()).launch {
              val flow = session.createEntryListCacheStreaming(state.directory)
              state.emitEntryListUpdater(flow)
            }
          }
        }
      }
    }
  }

  private inline fun initEditView(
    entry: StorageProto.Entry?,
    saveButtonString: String,
    cancelButtonString: String,
    selectionAfterCancel: StorageProto.Entry?,
    crossinline applyFunc: suspend (StorageProto.EntryInfo, StorageProto.EntryDetail) -> Unit
  ) {
    val editView = EntryDetailEditView(config, session, entry)
    detailScroll.setViewportView(editView)

    val saveButton = JButton(config.getString(saveButtonString))
    saveButton.isEnabled = false

    // TODO resource leak?
    CoroutineScope(config.executors.asCoroutineDispatcher()).launch {
      editView.readyState.collectLatest { ready ->
        SwingUtilities.invokeLater {
          saveButton.isEnabled = ready
        }
      }
    }
    saveButton.addActionListener {
      val entryInfo = editView.getEntryInfo()
      val details = editView.getEntryDetails()
      CoroutineScope(config.executors.asCoroutineDispatcher()).launch {
        applyFunc(entryInfo, details)
      }
    }

    val cancelButton = JButton(config.getString(cancelButtonString))
    cancelButton.addActionListener {
      setState {
        this.entryDetailState = if (selectionAfterCancel == null) {
          EntryDetailState.EmptyDetails(false)
        } else {
          EntryDetailState.ShowingDetails(selectionAfterCancel, false)
        }
      }
    }

    detailMenus.removeAll()
    detailMenus.isVisible = true
    detailMenus.add(saveButton)
    detailMenus.add(cancelButton)
    detailMenus.revalidate()
    detailMenus.repaint()
  }

  private fun updateDetailsView(detailState: EntryDetailState) {
    when (detailState) {
      is EntryDetailState.CreatingEntry -> SwingUtilities.invokeLater {
        list.isEnabled = false
        list.clearSelection()

        initEditView(
          null,
          "ENTRY_CREATE_SAVE",
          "ENTRY_CREATE_CANCEL",
          detailState.lastSelection
        ) { entryInfo, details ->
          val newEntry = session.createEntry(state.directory, entryInfo, details)

          setState {
            this.addEntry(newEntry)
            this.entryDetailState = EntryDetailState.ShowingDetails(newEntry, false)
          }
        }
      }
      is EntryDetailState.EditingEntry -> SwingUtilities.invokeLater {
        list.isEnabled = false
        list.setSelectedValue(detailState.entry, true)

        initEditView(
          detailState.entry,
          "ENTRY_EDIT_SAVE",
          "ENTRY_EDIT_CANCEL",
          detailState.entry
        ) { entryInfo, details ->
          session.updateEntry(state.directory, detailState.entry.id, entryInfo, details)

          setState {
            this.updateEntry(detailState.entry, entryInfo)
            this.entryDetailState = EntryDetailState.ShowingDetails(detailState.entry, false)
          }
        }
      }
      is EntryDetailState.ShowingDetails -> SwingUtilities.invokeLater {
        list.isEnabled = true
        if (!detailState.userTriggered) {
          SwingUtilities.invokeLater {
            list.setSelectedValue(detailState.entry, true)
          }
        }

        detailScroll.setViewportView(EntryDetailView(config, session, detailState.entry))

        detailMenus.removeAll()
        detailMenus.isVisible = true

        val editButton = JButton(config.getString("ENTRY_EDIT"))
        editButton.addActionListener {
          setState {
            this.entryDetailState = EntryDetailState.EditingEntry(detailState.entry)
          }
        }
        detailMenus.add(editButton)

        val deleteButton = JButton(config.getString("ENTRY_DELETE"))
        deleteButton.addActionListener {
          val confirmed = JOptionPane.showConfirmDialog(
            null,
            config.getString("CONFIRM_DELETE_ENTRY").format(detailState.entry.info.name),
            "",
            JOptionPane.YES_NO_OPTION
          )
          if (confirmed == JOptionPane.YES_OPTION) {
            CoroutineScope(config.executors.asCoroutineDispatcher()).launch {
              session.deleteEntry(detailState.entry.directory, detailState.entry.id)
            }
            setState {
              this.deleteEntry(detailState.entry)
              this.entryDetailState = EntryDetailState.EmptyDetails(false)
            }
          }
        }
        detailMenus.add(deleteButton)
        detailMenus.revalidate()
        detailMenus.repaint()
      }
      is EntryDetailState.EmptyDetails -> SwingUtilities.invokeLater {
        list.isEnabled = true
        if (!detailState.userTriggered) {
          SwingUtilities.invokeLater {
            list.clearSelection()
          }
        }

        detailScroll.setViewportView(JLabel(config.getString("SELECT_ENTRY")))
        detailMenus.removeAll()
        detailMenus.isVisible = false
        detailMenus.revalidate()
        detailMenus.repaint()
      }
    }
  }
}
