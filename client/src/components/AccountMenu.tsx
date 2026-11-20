import type { ReactNode } from "react";
import { useNavigate } from "@tanstack/react-router";
import {
  Activity,
  Bookmark,
  ChevronDown,
  Languages,
  LogOut,
  Palette,
  Settings,
  User,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useSession } from "@/api/auth";
import { clearToken } from "@/api/token";
import Avatar from "@/components/ui/Avatar";
import Button from "@/components/ui/Button";
import InPlaceSelect from "@/components/ui/InPlaceSelect";
import Popover from "@/components/ui/Popover";
import { locales, setLocale, useCopy, useLocale } from "@/i18n";
import { setTheme, themes, useTheme } from "@/lib/theme";

function Divider() {
  return (
    <div className="p-1">
      <div className="h-px bg-line" />
    </div>
  );
}

type MenuItemProps = {
  icon: LucideIcon;
  label: string;
  onClick: () => void;
};

function MenuItem({ icon: Icon, label, onClick }: MenuItemProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center gap-2.5 rounded-control px-2.5 py-2
        text-left text-label text-fg hover:bg-hover"
    >
      <Icon />
      {label}
    </button>
  );
}

/** The account avatar dropdown; signed-in and signed-out states share the theme and language rows. */
export default function AccountMenu() {
  const copy = useCopy();
  const locale = useLocale();
  const theme = useTheme();
  const navigate = useNavigate();
  const { signedIn, user } = useSession();

  const themeOptions = themes.map((value) => ({
    value,
    label: copy.theme[value],
  }));
  const languageOptions = locales.map((value) => ({
    value,
    label: value === "zh-CN" ? copy.language.zh : copy.language.en,
  }));

  function go(path: "/profile" | "/activity" | "/bookmarks" | "/settings") {
    navigate({ to: path });
  }

  function signOut(close: () => void) {
    clearToken();
    close();
    navigate({ to: "/" });
  }

  function selectRow(Icon: LucideIcon, label: string, control: ReactNode) {
    return (
      <div className="flex items-center gap-2.5 py-1 pr-1 pl-2.5">
        <div className="flex h-7 flex-1 items-center gap-2.5">
          <Icon />
          <span className="text-label">{label}</span>
        </div>
        {control}
      </div>
    );
  }

  const themeSelect = (
    <InPlaceSelect
      label={copy.theme.label}
      value={theme}
      options={themeOptions}
      onChange={setTheme}
    />
  );
  const languageSelect = (
    <InPlaceSelect
      label={copy.language.label}
      value={locale}
      options={languageOptions}
      onChange={setLocale}
    />
  );

  return (
    <div className="relative">
      <Popover
        align="end"
        label={copy.account.menuLabel}
        className="flex w-60 flex-col gap-0.5 p-1.5"
        trigger={(props) => (
          <button
            {...props}
            type="button"
            aria-label={copy.account.menuLabel}
            className="flex items-center gap-2 rounded-full p-1"
          >
            {user ? (
              <Avatar name={user.nickname || user.username} size="lg" />
            ) : (
              <span
                className="flex size-8 items-center justify-center rounded-full
                  border border-line bg-popover text-fg"
              >
                <User />
              </span>
            )}
            <ChevronDown size={14} className="text-fg-subtle" />
          </button>
        )}
      >
        {(close) => (
          <>
            {signedIn ? (
              <>
                <div className="flex items-center gap-2.5 px-2.5 py-2">
                  {user ? (
                    <>
                      <Avatar name={user.nickname || user.username} size="lg" />
                      <div className="flex min-w-0 flex-col">
                        <span className="truncate text-field font-bold">
                          {user.nickname || user.username}
                        </span>
                        <span
                          className="font-mono text-mono-meta text-fg-subtle"
                        >
                          {copy.account.uid(user.id)}
                        </span>
                      </div>
                    </>
                  ) : (
                    <span className="text-label text-fg-subtle">
                      {copy.account.loading}
                    </span>
                  )}
                </div>

                <Divider />

                <MenuItem
                  icon={User}
                  label={copy.account.profile}
                  onClick={() => {
                    close();
                    go("/profile");
                  }}
                />
                <MenuItem
                  icon={Activity}
                  label={copy.account.activity}
                  onClick={() => {
                    close();
                    go("/activity");
                  }}
                />
                <MenuItem
                  icon={Bookmark}
                  label={copy.account.bookmarks}
                  onClick={() => {
                    close();
                    go("/bookmarks");
                  }}
                />

                <Divider />

                {selectRow(Palette, copy.theme.label, themeSelect)}
                {selectRow(Languages, copy.language.label, languageSelect)}

                <MenuItem
                  icon={Settings}
                  label={copy.account.settings}
                  onClick={() => {
                    close();
                    go("/settings");
                  }}
                />

                <Divider />

                <MenuItem
                  icon={LogOut}
                  label={copy.account.signOut}
                  onClick={() => signOut(close)}
                />
              </>
            ) : (
              <>
                <div className="flex flex-col gap-2.5 px-2.5 pt-2 pb-2.5">
                  <p className="text-label font-normal text-fg-muted">
                    {copy.account.signedOutPrompt}
                  </p>
                  <div className="flex gap-2">
                    <div className="flex flex-1">
                      <Button
                        className="w-full"
                        onClick={() => {
                          close();
                          navigate({ to: "/login" });
                        }}
                      >
                        {copy.nav.login}
                      </Button>
                    </div>
                    <div className="flex flex-1">
                      <Button
                        variant="secondary"
                        className="w-full"
                        onClick={() => {
                          close();
                          navigate({ to: "/register" });
                        }}
                      >
                        {copy.account.signUp}
                      </Button>
                    </div>
                  </div>
                </div>

                <Divider />

                {selectRow(Palette, copy.theme.label, themeSelect)}
                {selectRow(Languages, copy.language.label, languageSelect)}
              </>
            )}
          </>
        )}
      </Popover>
    </div>
  );
}
