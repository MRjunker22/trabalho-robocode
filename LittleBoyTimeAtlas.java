package meusrobos;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

import java.awt.Color;

/**
 * Thunder - Robô ATACANTE.
 *
 * Estratégia:
 *  - Varre o campo constantemente com o radar até achar um alvo.
 *  - Trava o radar no inimigo para nunca perdê-lo de vista.
 *  - Avança agressivamente e, ao chegar perto, gira em volta do alvo.
 *  - Atira em todo scan, com mira preditiva e potência maior quanto mais perto.
 */
public class LittleBoyTimeAtlas extends AdvancedRobot {

    // 1 = sentido horário, -1 = sentido anti-horário (muda para dificultar a mira)
    private int direcao = 1;

    // Turno do último scan bem-sucedido (para saber quando o alvo foi perdido)
    private long ultimoScan = 0;

    @Override
    public void run() {
        // Cores: corpo, canhão, radar
        setColors(Color.YELLOW, Color.BLACK, Color.ORANGE);
        setBulletColor(Color.RED);

        // Corpo, canhão e radar giram de forma independente
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        while (true) {
            // Se o radar parou (alvo perdido), volta a varrer o campo
            if (getRadarTurnRemaining() == 0) {
                setTurnRadarRight(Double.POSITIVE_INFINITY);
            }

            // Sem alvo recente: procura andando em curva
            if (getTime() - ultimoScan > 3) {
                setTurnRight(30 * direcao);
                setAhead(150 * direcao);
            }

            execute(); // executa todos os comandos "set..." pendentes
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        ultimoScan = getTime();

        double distancia = e.getDistance();
        // Ângulo absoluto (em relação ao norte) até o inimigo
        double anguloAbsoluto = getHeadingRadians() + e.getBearingRadians();

        // ----- RADAR: trava no alvo (gira além do necessário para oscilar sobre ele) -----
        double giroRadar = Utils.normalRelativeAngle(anguloAbsoluto - getRadarHeadingRadians());
        setTurnRadarRightRadians(giroRadar * 2);

        // ----- POTÊNCIA: quanto mais perto, mais forte o tiro -----
        double potencia;
        if (distancia < 200) {
            potencia = 3.0;
        } else if (distancia < 500) {
            potencia = 2.0;
        } else {
            potencia = 1.5;
        }
        if (getEnergy() < 10) {
            potencia = Math.min(potencia, 1.0); // evita se desabilitar atirando
        }

        // ----- MIRA PREDITIVA: antecipa para onde o inimigo vai -----
        double velocidadeBala = 20 - 3 * potencia;
        double antecipacao = e.getVelocity()
                * Math.sin(e.getHeadingRadians() - anguloAbsoluto) / velocidadeBala;
        double giroCanhao = Utils.normalRelativeAngle(
                anguloAbsoluto - getGunHeadingRadians() + antecipacao);
        setTurnGunRightRadians(giroCanhao);

        // ----- TIRO: dispara sempre que possível -----
        setFire(potencia);

        // ----- MOVIMENTO -----
        // Longe: avança quase de frente. Perto: circula o alvo.
        double deslocamento = (distancia > 200) ? 0.3 : 1.2;
        setTurnRightRadians(Utils.normalRelativeAngle(
                e.getBearingRadians() + deslocamento * direcao));
        setAhead(150);

        // Muda o sentido de vez em quando para ficar imprevisível
        if (Math.random() < 0.05) {
            direcao = -direcao;
        }
    }

    @Override
    public void onHitRobot(HitRobotEvent e) {
        // Bateu em alguém: mira e atira à queima-roupa com força máxima
        double giro = Utils.normalRelativeAngleDegrees(
                getHeading() + e.getBearing() - getGunHeading());
        setTurnGunRight(giro);
        setFire(3);
        if (e.isMyFault()) {
            setBack(50);
        }
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        // Bateu na parede: inverte o sentido e se afasta
        direcao = -direcao;
        setBack(100);
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        // Levou um tiro: troca de sentido para dificultar o próximo
        direcao = -direcao;
    }

    @Override
    public void onBulletHit(BulletHitEvent e) {
        // Acertou o alvo: mantém a pressão (nenhuma ação extra necessária)
    }
}
