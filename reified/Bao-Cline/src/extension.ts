import delay from "delay"
import * as vscode from "vscode"
import { ClineProvider } from "./core/webview/ClineProvider"
import { createClineAPI } from "./exports"
import "./utils/path" // necessary to have access to String.prototype.toPosix
import { DIFF_VIEW_URI_SCHEME } from "./integrations/editor/DiffViewProvider"
import { JetBrainsCommunicator } from "./integrations/jetbrains/JetBrainsCommunicator"

let outputChannel: vscode.OutputChannel

export function activate(context: vscode.ExtensionContext) {
	outputChannel = vscode.window.createOutputChannel("Cline")
	context.subscriptions.push(outputChannel)

	outputChannel.appendLine("Cline extension activated")

	const defaultCommands = vscode.workspace
		.getConfiguration('bao-cline')
		.get<string[]>('allowedCommands') || [];

	if (!context.globalState.get('allowedCommands')) {
		context.globalState.update('allowedCommands', defaultCommands);
	}

	const sidebarProvider = new ClineProvider(context, outputChannel)

	context.subscriptions.push(
		vscode.window.registerWebviewViewProvider(ClineProvider.sideBarId, sidebarProvider, {
			webviewOptions: { retainContextWhenHidden: true },
		}),
	)

	context.subscriptions.push(
		vscode.commands.registerCommand("bao-cline.plusButtonClicked", async () => {
			outputChannel.appendLine("Plus button Clicked")
			await sidebarProvider.clearTask()
			await sidebarProvider.postStateToWebview()
			await sidebarProvider.postMessageToWebview({ type: "action", action: "chatButtonClicked" })
		}),
	)

	context.subscriptions.push(
		vscode.commands.registerCommand("bao-cline.mcpButtonClicked", () => {
			sidebarProvider.postMessageToWebview({ type: "action", action: "mcpButtonClicked" })
		}),
	)

	const openClineInNewTab = async () => {
		outputChannel.appendLine("Opening Cline in new tab")
		const tabProvider = new ClineProvider(context, outputChannel)
		const lastCol = Math.max(...vscode.window.visibleTextEditors.map((editor) => editor.viewColumn || 0))

		const hasVisibleEditors = vscode.window.visibleTextEditors.length > 0
		if (!hasVisibleEditors) {
			await vscode.commands.executeCommand("workbench.action.newGroupRight")
			await delay(100)
		}
		const targetCol = hasVisibleEditors ? Math.max(lastCol + 1, 1) : vscode.ViewColumn.Two

		const panel = vscode.window.createWebviewPanel(ClineProvider.tabPanelId, "Cline", targetCol, {
			enableScripts: true,
			retainContextWhenHidden: true,
			localResourceRoots: [context.extensionUri],
		})

		panel.iconPath = {
			light: vscode.Uri.joinPath(context.extensionUri, "assets", "icons", "leopard.png"),
			dark: vscode.Uri.joinPath(context.extensionUri, "assets", "icons", "leopard.png"),
		}
		tabProvider.resolveWebviewView(panel)

		await vscode.commands.executeCommand("workbench.action.lockEditorGroup")
	}

	context.subscriptions.push(vscode.commands.registerCommand("bao-cline.popoutButtonClicked", openClineInNewTab))
	context.subscriptions.push(vscode.commands.registerCommand("bao-cline.openInNewTab", openClineInNewTab))

	context.subscriptions.push(
		vscode.commands.registerCommand("bao-cline.settingsButtonClicked", () => {
			sidebarProvider.postMessageToWebview({ type: "action", action: "settingsButtonClicked" })
		}),
	)

	context.subscriptions.push(
		vscode.commands.registerCommand("bao-cline.historyButtonClicked", () => {
			sidebarProvider.postMessageToWebview({ type: "action", action: "historyButtonClicked" })
		}),
	)

	const diffContentProvider = new (class implements vscode.TextDocumentContentProvider {
		provideTextDocumentContent(uri: vscode.Uri): string {
			return Buffer.from(uri.query, "base64").toString("utf-8")
		}
	})()
	context.subscriptions.push(
		vscode.workspace.registerTextDocumentContentProvider(DIFF_VIEW_URI_SCHEME, diffContentProvider),
	)

	const handleUri = async (uri: vscode.Uri) => {
		const path = uri.path
		const query = new URLSearchParams(uri.query.replace(/\+/g, "%2B"))
		const visibleProvider = ClineProvider.getVisibleInstance()
		if (!visibleProvider) {
			return
		}
		switch (path) {
			case "/openrouter": {
				const code = query.get("code")
				if (code) {
					await visibleProvider.handleOpenRouterCallback(code)
				}
				break
			}
			default:
				break
		}
	}
	context.subscriptions.push(vscode.window.registerUriHandler({ handleUri }))

	// Initialize JetBrainsCommunicator
	const jetBrainsCommunicator = new JetBrainsCommunicator()
	jetBrainsCommunicator.connect().then(() => {
		outputChannel.appendLine("Connected to JetBrains tools")
	}).catch((error) => {
		outputChannel.appendLine(`Failed to connect to JetBrains tools: ${error.message}`)
	})

	context.subscriptions.push(
		vscode.commands.registerCommand("bao-cline.connectToJetBrains", async () => {
			const communicator = new JetBrainsCommunicator()
			await communicator.connect()
			outputChannel.appendLine("Connected to JetBrains tools")
		}),
	)

	context.subscriptions.push(
		vscode.commands.registerCommand("bao-cline.disconnectFromJetBrains", async () => {
			const communicator = new JetBrainsCommunicator()
			await communicator.disconnect()
			outputChannel.appendLine("Disconnected from JetBrains tools")
		}),
	)

	context.subscriptions.push(
		vscode.commands.registerCommand("bao-cline.sendMessageToJetBrains", async () => {
			const communicator = new JetBrainsCommunicator()
			const message = await vscode.window.showInputBox({ prompt: "Enter message to send to JetBrains tools" })
			if (message) {
				await communicator.sendMessage(message)
				outputChannel.appendLine(`Message sent to JetBrains tools: ${message}`)
			}
		}),
	)

	context.subscriptions.push(
		vscode.commands.registerCommand("bao-cline.receiveMessageFromJetBrains", async () => {
			const communicator = new JetBrainsCommunicator()
			const message = await communicator.receiveMessage()
			outputChannel.appendLine(`Message received from JetBrains tools: ${message}`)
			vscode.window.showInformationMessage(`Message received from JetBrains tools: ${message}`)
		}),
	)

	return createClineAPI(outputChannel, sidebarProvider)
}

export function deactivate() {
	outputChannel.appendLine("Cline extension deactivated")
}
