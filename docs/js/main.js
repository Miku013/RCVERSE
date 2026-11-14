document.addEventListener('DOMContentLoaded', () => {
  // モバイルメニュー開閉
  const menuToggle = document.querySelector('.menu-toggle');
  const navLinks = document.querySelector('.nav-links');

  if (menuToggle && navLinks) {
    menuToggle.addEventListener('click', () => {
      navLinks.classList.toggle('active');
    });

    // リンククリック時にメニューを閉じる
    navLinks.querySelectorAll('a').forEach(link => {
      link.addEventListener('click', () => {
        navLinks.classList.remove('active');
      });
    });
  }

  // スクロール時にナビゲーションバーのスタイル変更
  const navbar = document.querySelector('.navbar');
  window.addEventListener('scroll', () => {
    if (window.scrollY > 50) {
      navbar.style.borderBottomColor = 'rgba(56, 189, 248, 0.4)';
      navbar.style.boxShadow = '0 4px 20px rgba(0, 0, 0, 0.5)';
    } else {
      navbar.style.borderBottomColor = 'rgba(56, 189, 248, 0.2)';
      navbar.style.boxShadow = 'none';
    }
  });

  // ページ内アンカーのスムーススクロール（ヘッダーの高さを自動オフセット）
  document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function(e) {
      const targetId = this.getAttribute('href');
      if (targetId === '#' || !targetId) return;
      const targetElement = document.querySelector(targetId);
      if (targetElement) {
        e.preventDefault();
        const navHeight = navbar ? navbar.offsetHeight : 72;
        const targetPosition = targetElement.getBoundingClientRect().top + window.pageYOffset - navHeight - 12;
        window.scrollTo({
          top: targetPosition,
          behavior: 'smooth'
        });
        // URLのハッシュも更新
        history.pushState(null, null, targetId);
      }
    });
  });
});
