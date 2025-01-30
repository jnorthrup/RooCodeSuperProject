import { io } from "socket.io-client";

interface Message {
    type: string;
    content: string;
}

class WebSocketClient {
    private socket: any;

    constructor(serverUrl: string) {
        this.socket = io(serverUrl);

        this.socket.on("connect", () => {
            console.log("Connected to WebSocket server");
        });

        this.socket.on("disconnect", () => {
            console.log("Disconnected from WebSocket server");
        });

        this.socket.on("message", (data: string) => {
            const message: Message = JSON.parse(data);
            this.handleMessage(message);
        });
    }

    sendMessage(message: Message) {
        this.socket.emit("message", JSON.stringify(message));
    }

    handleMessage(message: Message) {
        switch (message.type) {
            case "response":
                console.log("Received response:", message.content);
                break;
            case "error":
                console.error("Received error:", message.content);
                break;
            default:
                console.warn("Unknown message type:", message.type);
                break;
        }
    }
}

export default WebSocketClient;
