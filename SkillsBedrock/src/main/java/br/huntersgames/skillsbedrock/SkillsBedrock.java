package br.huntersgames.skillsbedrock;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.component.FormImage; // Floodgate antigo: org.geysermc.cumulus.util.FormImage
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Recebe do skills.sk (so pelo console) os dados das skills e abre o menu
 * em formulario para o jogador Bedrock. Nao guarda nada: todos os dados
 * vem no comando.
 *
 * /skillformopen <uuid-do-jogador> <dono>;<poder>;<max>;<skill>|<nivel>|<xp>|<xpNecessario>|<bonus>;...
 */
public class SkillsBedrock extends JavaPlugin implements CommandExecutor {

    /** Nome, cor e icone de cada skill (mesma ordem do menu). */
    private record Meta(String chave, String cor, String nome, String icone) {}

    private static final List<Meta> METAS = List.of(
            new Meta("espada", "§c", "Espada", "textures/items/diamond_sword.png"),
            new Meta("lanca", "§4", "Lança", "textures/items/diamond_spear.png"),
            new Meta("machado", "§2", "Machado", "textures/items/diamond_axe.png"),
            new Meta("tridente", "§3", "Tridente", "textures/items/trident.png"),
            new Meta("maca", "§8", "Maça", "textures/items/mace.png"),
            new Meta("arco", "§e", "Arco", "textures/items/bow_standby.png"),
            new Meta("besta", "§6", "Besta", "textures/items/crossbow_standby.png"),
            new Meta("pa", "§6", "Pá", "textures/items/diamond_shovel.png"),
            new Meta("picareta", "§b", "Picareta", "textures/items/diamond_pickaxe.png"),
            new Meta("pesca", "§9", "Vara de Pesca", "textures/items/fishing_rod_uncast.png")
    );

    private record Skill(Meta meta, int nivel, double xp, double necessario, String bonus) {}

    private record Dados(String dono, String poder, int max, List<Skill> skills) {}

    @Override
    public void onEnable() {
        getCommand("skillformopen").setExecutor(this);
        getLogger().info("SkillsBedrock ativo.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // so o console (o skills.sk) pode usar
        if (!(sender instanceof ConsoleCommandSender)) {
            return true;
        }
        if (args.length < 2) {
            return true;
        }
        try {
            UUID id = UUID.fromString(args[0]);
            FloodgateApi api = FloodgateApi.getInstance();
            if (!api.isFloodgatePlayer(id)) {
                return true;
            }
            String payload = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            Dados dados = ler(payload);
            abrirLista(id, dados);
        } catch (Exception e) {
            getLogger().warning("Falha ao abrir o menu Bedrock: " + e);
        }
        return true;
    }

    // ---------- leitura dos dados ----------

    private Dados ler(String payload) {
        String[] p = payload.split(";", -1);
        String dono = cor(p[0]);
        String poder = p[1].trim();
        int max = (int) Double.parseDouble(p[2].trim());
        List<Skill> skills = new ArrayList<>();
        for (int i = 3; i < p.length; i++) {
            String[] f = p[i].split("\\|", -1);
            if (f.length < 5) {
                continue;
            }
            Meta meta = null;
            for (Meta m : METAS) {
                if (m.chave().equals(f[0].trim())) {
                    meta = m;
                    break;
                }
            }
            if (meta == null) {
                continue;
            }
            skills.add(new Skill(meta,
                    (int) Double.parseDouble(f[1].trim()),
                    Double.parseDouble(f[2].trim()),
                    Double.parseDouble(f[3].trim()),
                    cor(f[4])));
        }
        return new Dados(dono, poder, max, skills);
    }

    private static String cor(String t) {
        return t.replace('&', '§');
    }

    // ---------- tela principal ----------

    private void abrirLista(UUID id, Dados d) {
        SimpleForm.Builder b = SimpleForm.builder()
                .title("§8Skills de " + d.dono())
                .content("§6Poder total: §e" + d.poder() + "\n§7Toque numa skill para ver os detalhes.");
        for (Skill s : d.skills()) {
            String linha2 = s.nivel() >= d.max()
                    ? "§aNível §e" + s.nivel() + " §a(MÁXIMO)"
                    : "§7Nível §e" + s.nivel() + "§7/" + d.max() + " §8| §a" + pct(s) + "%";
            b.button(s.meta().cor() + s.meta().nome() + "\n" + linha2,
                    FormImage.Type.PATH, s.meta().icone());
        }
        b.validResultHandler(r -> {
            int i = r.clickedButtonId();
            if (i >= 0 && i < d.skills().size()) {
                abrirDetalhe(id, d, d.skills().get(i));
            }
        });
        FloodgateApi.getInstance().sendForm(id, b.build());
    }

    // ---------- tela de detalhes ----------

    private void abrirDetalhe(UUID id, Dados d, Skill s) {
        boolean maximo = s.nivel() >= d.max();
        String barra = maximo ? "§a" + "|".repeat(20) : barra(s);
        String xp = maximo
                ? "§aMÁXIMO"
                : "§f" + fmt(s.xp()) + "§7/§f" + fmt(s.necessario()) + " §8(" + pct(s) + "%)";
        String texto = "§7Nível: §e" + s.nivel() + "§7/" + d.max()
                + "\n" + barra
                + "\n§7XP: " + xp
                + "\n\n§7Bónus: " + s.bonus();
        SimpleForm.Builder b = SimpleForm.builder()
                .title(s.meta().cor() + s.meta().nome())
                .content(texto)
                .button("« Voltar");
        b.validResultHandler(r -> abrirLista(id, d));
        FloodgateApi.getInstance().sendForm(id, b.build());
    }

    // ---------- util ----------

    private static int pct(Skill s) {
        if (s.necessario() <= 0) {
            return 0;
        }
        return (int) Math.floor((s.xp() / s.necessario()) * 100);
    }

    private static String barra(Skill s) {
        int cheio = s.necessario() <= 0 ? 0 : (int) Math.floor(20 * s.xp() / s.necessario());
        cheio = Math.max(0, Math.min(20, cheio));
        return "§a" + "|".repeat(cheio) + "§7" + "|".repeat(20 - cheio);
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
