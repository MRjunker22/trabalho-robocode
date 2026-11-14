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
 * Guardian - Robô DEFENSOR.
 *
 * Estratégia:
 *  - Mantém o radar travado no inimigo para ter informação constante.
 *  - Orbita o alvo a uma distância segura (aprox. 350 px).
 *  - Detecta quedas de energia do inimigo (sinal de que ele atirou) e muda
 *    de direção para desviar da bala.
 *  - Evita paredes antes de encostar nelas.
 *  - Só atira em boas oportunidades: canhão alinhado, alvo a uma distância
 *    razoável e energia própria suficiente.
 */
public class FatManTimeAtlas extends AdvancedRobot {

    private static final double DISTANCIA_IDEAL = 350;  // distância que tenta manter
    private static final double DISTANCIA_MAX_TIRO = 450; // não gasta energia além disso
    private static final double MARGEM_PAREDE = 60;     // folga em relação às bordas

    private double energiaInimigo = 100; // energia do inimigo no scan anterior
    private int direcao = 1;             // 1 ou -1: sentido da órbita

    @Override
    public void run() {
        // Cores: corpo, canhão, radar
        setColors(Color.BLUE, Color.CYAN, Color.WHITE);
        setBulletColor(Color.CYAN);

        // Corpo, canhão e radar giram de forma independente
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        setTurnRadarRight(Double.POSITIVE_INFINITY);

        while (true) {
            // Se o radar parou (alvo perdido), volta a varrer o campo
            if (getRadarTurnRemaining() == 0) {
                setTurnRadarRight(Double.POSITIVE_INFINITY);
            }
            execute(); // executa todos os comandos "set..." pendentes
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        double distancia = e.getDistance();
        double anguloAbsoluto = getHeadingRadians() + e.getBearingRadians();

        // ----- RADAR: trava no alvo -----
        double giroRadar = Utils.normalRelativeAngle(anguloAbsoluto - getRadarHeadingRadians());
        setTurnRadarRightRadians(giroRadar * 2);

        // ----- DESVIO: queda de energia entre 0.1 e 3.0 = inimigo atirou -----
        double queda = energiaInimigo - e.getEnergy();
        if (queda > 0.09 && queda <= 3.01) {
            direcao = -direcao; // muda de sentido para escapar da bala
        }
        energiaInimigo = e.getEnergy();

        // ----- MOVIMENTO: órbita em torno do inimigo mantendo distância -----
        // Se estiver longe, aproxima levemente; se estiver perto, se afasta.
        double ajuste = (distancia - DISTANCIA_IDEAL) / DISTANCIA_IDEAL * 0.6;
        ajuste = Math.max(-0.6, Math.min(0.6, ajuste));

        // Evita paredes: se o próximo trecho sairia do campo, inverte o sentido
        if (vaiBaterNaParede()) {
            direcao = -direcao;
        }

        setTurnRightRadians(Utils.normalRelativeAngle(
                e.getBearingRadians() + Math.PI / 2 - ajuste * direcao));
        setAhead(100 * direcao);

        // ----- MIRA PREDITIVA -----
        double potencia = escolherPotencia(distancia);
        double velocidadeBala = 20 - 3 * potencia;
        double antecipacao = e.getVelocity()
                * Math.sin(e.getHeadingRadians() - anguloAbsoluto) / velocidadeBala;
        double giroCanhao = Utils.normalRelativeAngle(
                anguloAbsoluto - getGunHeadingRadians() + antecipacao);
        setTurnGunRightRadians(giroCanhao);

        // ----- TIRO: apenas em boas oportunidades -----
        boolean canhaoAlinhado = Math.abs(getGunTurnRemaining()) < 8;
        boolean alvoNoAlcance = distancia <= DISTANCIA_MAX_TIRO;
        boolean energiaSuficiente = getEnergy() > 10 || e.getEnergy() == 0;

        if (getGunHeat() == 0 && canhaoAlinhado && alvoNoAlcance && energiaSuficiente) {
            setFire(potencia);
        }
    }

    /** Escolhe a potência do tiro: forte e certeira de perto, econômica de longe. */
    private double escolherPotencia(double distancia) {
        double potencia;
        if (distancia < 200) {
            potencia = 3.0;
        } else if (distancia < 350) {
            potencia = 2.0;
        } else {
            potencia = 1.2;
        }
        if (getEnergy() < 25) {
            potencia = Math.min(potencia, 1.0); // poupa energia quando está fraco
        }
        return potencia;
    }

    /** Projeta o movimento à frente e verifica se terminaria perto de uma parede. */
    private boolean vaiBaterNaParede() {
        double rumo = getHeadingRadians() + (direcao == 1 ? 0 : Math.PI);
        double proximoX = getX() + Math.sin(rumo) * 120;
        double proximoY = getY() + Math.cos(rumo) * 120;

        return proximoX < MARGEM_PAREDE
                || proximoX > getBattleFieldWidth() - MARGEM_PAREDE
                || proximoY < MARGEM_PAREDE
                || proximoY > getBattleFieldHeight() - MARGEM_PAREDE;
    }

    @Override
    public void onBulletHit(BulletHitEvent e) {
        // Nosso tiro acertou: atualiza a energia do inimigo para não confundir
        // a perda de energia com um tiro dele.
        energiaInimigo = e.getEnergy();
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        // Foi atingido: muda de sentido para dificultar os próximos tiros
        direcao = -direcao;
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        // Bateu na parede: inverte o sentido e se afasta
        direcao = -direcao;
        setBack(80);
    }

    @Override
    public void onHitRobot(HitRobotEvent e) {
        // Contato indesejado: recua para recuperar distância
        direcao = -direcao;
        setBack(120);
    }
}
