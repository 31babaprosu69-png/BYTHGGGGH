package pro.webhm.playerinteractions;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConsentBook {
   private final Map<UUID, ConsentBook.Invite> invites = new LinkedHashMap<>();
   private final Map<UUID, UUID> reserved = new HashMap<>();
   private final Map<UUID, Long> cooldown = new HashMap<>();

   public ConsentBook.Invite offer(UUID from, UUID to, int action, long now) {
      if (!from.equals(to)
         && Actions.supported(action)
         && !this.reserved.containsKey(from)
         && !this.reserved.containsKey(to)
         && now >= this.cooldown.getOrDefault(from, 0L)) {
         ConsentBook.Invite invite = new ConsentBook.Invite(UUID.randomUUID(), from, to, action, now + 400L);
         this.invites.put(invite.id, invite);
         this.reserved.put(from, invite.id);
         this.reserved.put(to, invite.id);
         this.cooldown.put(from, now + 40L);
         return invite;
      } else {
         return null;
      }
   }

   public ConsentBook.Invite get(UUID id) {
      return this.invites.get(id);
   }

   public UUID reserved(UUID player) {
      return this.reserved.get(player);
   }

   public ConsentBook.Invite answer(UUID actor, UUID id, boolean accept, long now) {
      ConsentBook.Invite invite = this.invites.get(id);
      if (invite != null && actor.equals(invite.recipient) && now < invite.expires) {
         this.invites.remove(id);
         if (!accept) {
            this.release(id);
         }

         return invite;
      } else {
         return null;
      }
   }

   public boolean participant(UUID actor, UUID id) {
      return id.equals(this.reserved.get(actor));
   }

   public void release(UUID id) {
      this.invites.remove(id);
      this.reserved.values().removeIf(id::equals);
   }

   public List<ConsentBook.Invite> invitations() {
      return List.copyOf(this.invites.values());
   }

   public void removePlayer(UUID id) {
      UUID token = this.reserved.get(id);
      if (token != null) {
         this.release(token);
      }

      this.cooldown.remove(id);
   }

   public void clear() {
      this.invites.clear();
      this.reserved.clear();
      this.cooldown.clear();
   }

   public static record Invite(UUID id, UUID initiator, UUID recipient, int action, long expires) {
   }
}
