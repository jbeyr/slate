package slate.module.impl.other;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import slate.module.Module;
import slate.utility.Utils;

import java.text.DecimalFormat;
import java.util.Optional;
import java.util.Random;

public class QuickMathsSolver extends Module {
    private static final String QUICK_MATHS_PREFIX = "QUICK MATHS! Solve: ";
    private final Random random = new Random();
    private int delayTicks = 0;
    private Optional<String> queuedAnswer = Optional.empty();
    private static int minResponseTicks;
    private static int maxResponseTicks;
    private static int replyAfterXSolvers;
    private static boolean enabled;

    private int currentSolvers = 0;
    private boolean readyToSolve = false;

    public QuickMathsSolver() {
        super("Quick Maths Solver", category.other);
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if(!enabled) return;
        String message = event.message.getUnformattedText();

        if (message.startsWith(QUICK_MATHS_PREFIX)) {
            resetState();
            String expression = message.substring(QUICK_MATHS_PREFIX.length());
            try {
                int result = evaluateExpression(expression);
                queuedAnswer = Optional.of(String.valueOf(result));
                delayTicks = random.nextInt(maxResponseTicks - minResponseTicks) + minResponseTicks;
                if(replyAfterXSolvers == 0) readyToSolve = true;
                DecimalFormat df = new DecimalFormat("#.##");
                Utils.sendMessage("Prepared answer '" + result + "'. Will send after " + replyAfterXSolvers + " solvers and " + df.format(delayTicks/20.0) + "s");
            } catch (IllegalArgumentException e) {
                System.err.println("Failed to solve expression: " + expression);
                e.printStackTrace();
            }
        } else if (message.contains("QUICK MATHS!") && message.contains("answered in")) {
            currentSolvers++;
            if (currentSolvers >= replyAfterXSolvers) readyToSolve = true;
        } else if (message.contains("QUICK MATHS OVER!")) {
            resetState();
        }
    }

    private void resetState() {
        currentSolvers = 0;
        readyToSolve = false;
        queuedAnswer = Optional.empty();
        delayTicks = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.PlayerTickEvent event) {
        if(!enabled || !readyToSolve || !queuedAnswer.isPresent()) {
            return;
        }

        if (event.phase == TickEvent.Phase.START) {
            if (delayTicks <= 0) {
                Minecraft.getMinecraft().thePlayer.sendChatMessage(queuedAnswer.get());
                resetState();
            } else {
                delayTicks--;
            }
        }
    }

    private int evaluateExpression(String expression) {
        expression = expression.replaceAll("\\s+", ""); // Remove all whitespace
        return evalRecursive(expression);
    }

    /**
     * Recursively evaluates an elementary math expression only using +,-,x,/ and () operators for grouping.
     *
     * @param expression an elementary math expression only using +,-,x,/ and () operators for grouping.
     * @return the computed result
     */
    private int evalRecursive(String expression) {
        if (expression.startsWith("(") && expression.endsWith(")")) {
            return evalRecursive(expression.substring(1, expression.length() - 1));
        }

        int parenCount = 0;
        int lastOperatorIndex = -1;
        for (int i = expression.length() - 1; i >= 0; i--) {
            char c = expression.charAt(i);
            if (c == ')') parenCount++;
            else if (c == '(') parenCount--;
            else if (parenCount == 0 && (c == '+' || c == '-')) {
                lastOperatorIndex = i;
                break;
            }
        }

        if (lastOperatorIndex != -1) {
            int left = evalRecursive(expression.substring(0, lastOperatorIndex));
            int right = evalRecursive(expression.substring(lastOperatorIndex + 1));
            return expression.charAt(lastOperatorIndex) == '+' ? left + right : left - right;
        }

        for (int i = expression.length() - 1; i >= 0; i--) {
            char c = expression.charAt(i);
            if (c == ')') parenCount++;
            else if (c == '(') parenCount--;
            else if (parenCount == 0 && (c == 'x' || c == '*' || c == '/')) {
                int left = evalRecursive(expression.substring(0, i));
                int right = evalRecursive(expression.substring(i + 1));
                return c == 'x' || c == '*' ? left * right : left / right;
            }
        }

        try {
            return Integer.parseInt(expression);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid expression: '" + expression + "'");
        }
    }
}